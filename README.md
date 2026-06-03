# live-rec-pi

A Raspberry Pi–based live audio recorder. Captures multichannel audio from a USB mixer, splits each channel into its own WAV file, and exposes a web interface accessible from a tablet on the local network.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Web browser (tablet)                                   │
│  http://pi:8000                                         │
└────────────────────┬────────────────────────────────────┘
                     │ HTTP / WebSocket
┌────────────────────▼────────────────────────────────────┐
│  API  (FastAPI)           api/                          │
│  • REST endpoints                                       │
│  • WebSocket: daemon status (/ws)                       │
│  • WebSocket: live meters  (/ws/meters) ← OSC           │
│  • Serves built web UI as static files                  │
└──────────────┬──────────────────────┬───────────────────┘
               │ Unix socket (IPC)    │ OSC / UDP
┌──────────────▼──────────┐  ┌────────▼──────────────────┐
│  Daemon                 │  │  Physical mixer (XR18)    │
│  daemon/                │  │  192.168.x.x:10023        │
│  • ALSA recording       │  │  • Channel names          │
│  • Per-channel WAV      │  │  • Live meter levels      │
│  • Playback             │  └───────────────────────────┘
└─────────────────────────┘
```

## Modules

| Directory | Description |
|---|---|
| `daemon/` | Python systemd service. Handles ALSA recording via ffmpeg, per-channel WAV output, Unix socket IPC. |
| `api/` | FastAPI server. Proxies IPC commands, connects to the mixer over OSC, serves the web UI. |
| `web/` | React + TypeScript + Tailwind UI. Built to `web/dist/` and served by the API. |

---

## Quick start — Docker (laptop/development)

No hardware required. Uses a mock daemon that simulates all recording and playback state.

**Requirements:** Docker, Docker Compose

```bash
git clone https://github.com/campidelli/live-rec-pi.git
cd live-rec-pi
docker compose up --build
```

Open `http://localhost:8000` in your browser.

The mock daemon simulates:
- A connected XR18 with 18 channels
- Recording sessions with elapsed timer
- Playback with position tracking
- Channel rename

To rebuild after code changes:
```bash
docker compose up --build
```

---

## Installation on a Raspberry Pi

### Requirements

- Raspberry Pi OS (Bookworm or later)
- Python 3.10+
- Node.js 20+ (for building the web UI)
- System packages: `alsa-utils`, `ffmpeg`
- A supported USB mixer (currently: Behringer XR18)

### 1. Install system dependencies

```bash
sudo apt update
sudo apt install -y alsa-utils ffmpeg python3-pip nodejs npm rsync
```

### 2. Install the daemon

```bash
git clone https://github.com/campidelli/live-rec-pi.git
cd live-rec-pi/daemon
sudo bash install.sh
```

### 3. Install the API and web UI

```bash
cd ../api
sudo bash install.sh
```

This builds the web UI, installs Python dependencies, copies everything to `/opt/live-rec-pi/`, and starts both systemd services.

### 4. Access the interface

Open `http://<pi-ip>:8000` from any device on the local network.

---

## Configuration

### Daemon — `daemon/config/daemon.yaml`

| Field | Description | Default |
|---|---|---|
| `socket_path` | Unix socket path | `/tmp/live-rec.sock` |
| `recordings_dir` | Fallback recordings root | `/home/pi/recordings` |
| `log_level` | `DEBUG` / `INFO` / `WARNING` | `INFO` |
| `recording.output_path` | Where session folders are created | `/home/pi/recordings` |
| `recording.session_template` | `strftime` pattern for session folder | `%Y-%m-%d_%H-%M-%S` |
| `recording.format` | Output format | `wav` |

### API — `api/config/api.yaml`

| Field | Description | Default |
|---|---|---|
| `socket_path` | Must match daemon's `socket_path` | `/tmp/live-rec.sock` |
| `host` | Bind address | `0.0.0.0` |
| `port` | HTTP port | `8000` |
| `log_level` | `DEBUG` / `INFO` / `WARNING` | `INFO` |

---

## Adding a new mixer

1. Create `daemon/config/mixers/<id>.yaml` (copy `xr18.yaml` as a template):

```yaml
id: my_mixer
name: My Mixer
channels:
  - channel-01
  - channel-02
  # one entry per physical channel
sample_rate: 48000
input_format: pcm_s32le
output_codec: pcm_s24le
```

The `id` must appear (case-insensitive) in `arecord -l` output for auto-detection.

2. Create `api/src/api/mixer_clients/<id>.py` implementing `MixerClient` (`discover`, `get_channel_state`, `stream_meters`).

3. Register the new class in `api/src/api/mixer_clients/registry.py`.

4. Restart both services.

---

## Services

Both are managed by systemd and start automatically on boot:

```bash
sudo systemctl status live-rec-daemon
sudo systemctl status live-rec-api

sudo journalctl -u live-rec-daemon -f
sudo journalctl -u live-rec-api -f
```

---

## Repository layout

```
live-rec-pi/
  daemon/           Python recording daemon
    config/         daemon.yaml + mixers/*.yaml
    src/daemon/     Source modules
    mock_server.py  Development mock (used by Docker)
    Dockerfile      Real daemon image (alsa-utils + ffmpeg)
    Dockerfile.mock Mock daemon image (no hardware deps)
    install.sh      Pi installer
  api/              FastAPI server + static file host
    config/         api.yaml
    src/api/        Source modules
      routers/      HTTP + WebSocket endpoints
      mixer_clients/ OSC/protocol clients per mixer
    Dockerfile      Multi-stage build (web + Python)
    install.sh      Pi installer
  web/              React/TypeScript/Tailwind UI
    src/
      api/          Typed fetch client + WebSocket hooks
      components/   UI components
      hooks/        Shared state hooks
  docker/           Config overrides for Docker
  docker-compose.yml Local development stack
