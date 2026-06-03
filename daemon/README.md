# live-rec-pi — Daemon

A systemd service that records multichannel audio from a USB mixer on a Raspberry Pi. Each recording session produces one WAV file per channel, split and named by channel ID.

## Requirements

- Raspberry Pi running Raspberry Pi OS (or any Debian-based distro)
- Python 3.10+
- System packages: `alsa-utils`, `ffmpeg`
- A supported USB mixer (currently: Behringer XR18)

---

## Installation

### 1. Install system dependencies

```bash
sudo apt update
sudo apt install -y alsa-utils ffmpeg python3-pip rsync
```

### 2. Install the daemon

Clone the repo and run the install script as root:

```bash
git clone https://github.com/campidelli/live-rec-pi.git
cd live-rec-pi/daemon
sudo bash install.sh
```

This will:
- Install Python dependencies (`pyyaml`)
- Copy the project to `/opt/live-rec-pi/`
- Install and enable the `live-rec-daemon` systemd unit

### 3. Edit the config

```bash
sudo nano /opt/live-rec-pi/daemon/config/daemon.yaml
```

Key fields:

| Field | Description | Default |
|---|---|---|
| `socket_path` | Unix socket path for IPC | `/tmp/live-rec.sock` |
| `recordings_dir` | Fallback recordings root | `/home/pi/recordings` |
| `log_level` | `DEBUG`, `INFO`, `WARNING` | `INFO` |
| `recording.output_path` | Where session folders are written | `/home/pi/recordings` |
| `recording.session_template` | `strftime` pattern for session folder name | `%Y-%m-%d_%H-%M-%S` |
| `recording.format` | Output format (`wav`) | `wav` |

### 4. Start the service

```bash
sudo bash install.sh
```

The install script enables and starts the service in one step. The daemon will start automatically on every subsequent boot — no further configuration needed.

To verify it is running:

```bash
sudo systemctl status live-rec-daemon
```

---

## Operation

All control is done by sending JSON commands over the Unix socket. The simplest way is with `socat`:

```bash
echo '{"command":"status"}' | socat - UNIX-CONNECT:/tmp/live-rec.sock
```

### Commands

#### `status`
Returns the full daemon state.
```bash
echo '{"command":"status"}' | socat - UNIX-CONNECT:/tmp/live-rec.sock
```

#### `start`
Starts a new recording session. Creates a timestamped subfolder and begins writing one file per channel.
```bash
echo '{"command":"start"}' | socat - UNIX-CONNECT:/tmp/live-rec.sock
# → {"status": "ok", "session": "2024-01-15_20-30-00"}
```

#### `stop`
Stops the current recording and finalizes all files.
```bash
echo '{"command":"stop"}' | socat - UNIX-CONNECT:/tmp/live-rec.sock
```

#### `list_mixers`
Lists all supported mixers and which one is currently active.
```bash
echo '{"command":"list_mixers"}' | socat - UNIX-CONNECT:/tmp/live-rec.sock
# → {"status": "ok", "mixers": [{"id": "xr18", "name": "X Air 18", "active": true}]}
```

#### `load_mixer`
Switches the active mixer. Stops any in-progress recording or playback first.
```bash
echo '{"command":"load_mixer", "id":"xr18"}' | socat - UNIX-CONNECT:/tmp/live-rec.sock
```

#### `get_channel_ids`
Returns the current channel ID list for the active mixer.
```bash
echo '{"command":"get_channel_ids"}' | socat - UNIX-CONNECT:/tmp/live-rec.sock
# → {"status": "ok", "channels": [{"index": 0, "id": "channel-01"}, ...]}
```

#### `set_channel_id`
Renames a channel. Takes effect on the next `start` (does not affect a recording in progress).
```bash
echo '{"command":"set_channel_id", "index":0, "channel_id":"kick"}' | socat - UNIX-CONNECT:/tmp/live-rec.sock
```

#### `list_formats`
Lists supported output formats.
```bash
echo '{"command":"list_formats"}' | socat - UNIX-CONNECT:/tmp/live-rec.sock
# → {"status": "ok", "formats": [{"id": "wav", "description": "Waveform Audio (PCM, lossless)"}]}
```

#### `get_recording_settings`
Returns current recording settings (output path, session template, format).
```bash
echo '{"command":"get_recording_settings"}' | socat - UNIX-CONNECT:/tmp/live-rec.sock
```

#### `set_recording_settings`
Updates one or more recording settings. Cannot be changed while recording.
```bash
echo '{"command":"set_recording_settings", "output_path":"/mnt/usb/recordings"}' | socat - UNIX-CONNECT:/tmp/live-rec.sock
echo '{"command":"set_recording_settings", "session_template":"%Y%m%d-%H%M%S", "format":"wav"}' | socat - UNIX-CONNECT:/tmp/live-rec.sock
```

### Session layout

Each `start` command creates a new session folder:

```
/home/pi/recordings/
  2024-01-15_20-30-00/
    01-kick.wav
    02-snare.wav
    03-channel-03.wav
    ...
    18-channel-18.wav
  2024-01-15_22-00-00/
    01-kick.wav
    ...
```

File naming: `<index>-<channel-id>.<format>` where index is zero-padded to two digits.

---

## Adding a new mixer

Create a YAML file in `config/mixers/` using the existing `xr18.yaml` as a template:

```yaml
id: my_mixer
name: My Mixer
channels:
  - channel-01
  - channel-02
  # ... one entry per channel
sample_rate: 48000
input_format: pcm_s32le
output_codec: pcm_s24le
```

The `id` must match a substring found in `arecord -l` output for auto-detection to work. Restart the daemon after adding a new mixer file.

---

## Troubleshooting

### Service won't start — missing tools

```
Missing required system tools: 'arecord' (package: alsa-utils), 'ffmpeg' (package: ffmpeg)
```

Install the missing packages:
```bash
sudo apt install -y alsa-utils ffmpeg
```

### Mixer not detected

Check that `arecord -l` lists the mixer:
```bash
arecord -l
```
The mixer's `id` from its YAML must appear (case-insensitive) in that output. If the device is listed under a different name, update the `id` field in the mixer YAML.

### Recording starts but produces empty or corrupt files

Check that the ALSA device is not already in use by another process:
```bash
fuser /dev/snd/*
```

Verify the `input_format` in the mixer YAML matches what the device actually reports:
```bash
arecord -D hw:0 --dump-hw-params /dev/null
```

### Output path not writable

If `set_recording_settings` returns an error about the path not being writable, check mount status and permissions:
```bash
lsblk
ls -la /mnt/usb/
```

### View live logs

```bash
sudo journalctl -u live-rec-daemon -f
```

Increase verbosity by setting `log_level: DEBUG` in `daemon.yaml` and restarting:
```bash
sudo systemctl restart live-rec-daemon
```

### Socket already in use after crash

If the daemon crashed, the socket file may be left behind. The daemon cleans it up on start, but if the port is held by another process:
```bash
rm /tmp/live-rec.sock
sudo systemctl restart live-rec-daemon
```
