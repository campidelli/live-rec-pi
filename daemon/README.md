# live-rec-pi — Daemon

Python systemd service that captures multichannel audio from a USB mixer via ALSA and writes one WAV file per channel per session. Controlled by the API over a Unix socket using a JSON-lines protocol.

---

## External dependencies

| Tool | Package | Purpose |
|---|---|---|
| `arecord` | `alsa-utils` | Detect the mixer ALSA device via `arecord -l` |
| `ffmpeg` | `ffmpeg` | Capture audio from ALSA and write per-channel WAV files |
| `ffprobe` | `ffmpeg` | Read WAV file duration for playback |

The daemon checks all three on startup and exits with a clear message if any are missing.

---

## Installation on Pi

### 1. Install system dependencies

```bash
sudo apt update
sudo apt install -y alsa-utils ffmpeg python3-pip rsync
```

### 2. Run the installer

```bash
cd live-rec-pi/daemon
sudo bash install.sh
```

This installs Python dependencies (`pyyaml`), copies the project to `/opt/live-rec-pi/`, installs and enables the `live-rec-daemon` systemd unit, and starts the service.

The service starts automatically on every subsequent boot.

### 3. Verify

```bash
sudo systemctl status live-rec-daemon
sudo journalctl -u live-rec-daemon -f
```

---

## Configuration — `config/daemon.yaml`

```yaml
socket_path: /tmp/live-rec.sock
recordings_dir: /home/pi/recordings
log_level: INFO

recording:
  output_path: /home/pi/recordings
  session_template: "%Y-%m-%d_%H-%M-%S"
  filename: recording
  format: wav
```

| Field | Description |
|---|---|
| `socket_path` | Unix socket the daemon listens on |
| `recordings_dir` | Fallback path (used if `output_path` is not set) |
| `log_level` | `DEBUG`, `INFO`, or `WARNING` |
| `recording.output_path` | Root directory for session folders |
| `recording.session_template` | `strftime` pattern for the session subfolder name |
| `recording.format` | Output audio format (`wav`) |

---

## Mixer config — `config/mixers/<id>.yaml`

One file per supported mixer. Example: `xr18.yaml`

```yaml
id: xr18
name: X Air 18
channels:
  - channel-01
  - channel-02
  # ... 18 entries total
sample_rate: 48000
input_format: pcm_s32le    # ALSA capture format
output_codec: pcm_s24le    # WAV output codec (saves ~25% vs 32-bit)
```

The `id` field must appear (case-insensitive) in `arecord -l` output for auto-detection. The filename stem must match the `id` field.

---

## Session layout

Each `start` command creates a timestamped subfolder:

```
/home/pi/recordings/
  2024-01-15_20-30-00/
    01-kick.wav
    02-snare.wav
    03-channel-03.wav
    ...
    18-channel-18.wav
```

File naming: `<zero-padded-index>-<channel-id>.<format>`

---

## IPC protocol

All communication is JSON lines over the Unix socket. Use `socat` to test manually:

```bash
echo '{"command":"status"}' | socat - UNIX-CONNECT:/tmp/live-rec.sock
```

### Commands

#### `status`
Returns the full daemon state.
```json
{"command": "status"}
```
Response fields: `status` (`"recording"` or `"idle"`), `connected`, `device`, `device_model`, `mixer_id`, `channels`, `recording_session`, `recording_files`, `record_time`, `playing`, `paused`, `play_session`, `play_file`, `position`, `duration`, `sessions`, `storage_path`, `storage_free_bytes`, `storage_total_bytes`.

#### `start`
Starts a new recording session.
```json
{"command": "start"}
→ {"status": "ok", "session": "2024-01-15_20-30-00"}
```

#### `stop`
Stops the current recording and finalizes all WAV files.
```json
{"command": "stop"}
→ {"status": "ok"}
```

#### `list_mixers`
```json
{"command": "list_mixers"}
→ {"status": "ok", "mixers": [{"id": "xr18", "name": "X Air 18", "active": true}]}
```

#### `load_mixer`
Switches the active mixer. Stops any in-progress recording or playback first.
```json
{"command": "load_mixer", "id": "xr18"}
→ {"status": "ok", "mixer": "xr18"}
```

#### `get_channel_ids`
```json
{"command": "get_channel_ids"}
→ {"status": "ok", "channels": [{"index": 0, "id": "channel-01"}, ...]}
```

#### `set_channel_id`
Renames a channel. Takes effect on the next `start`.
```json
{"command": "set_channel_id", "index": 0, "channel_id": "kick"}
→ {"status": "ok", "channels": [...]}
```

#### `list_formats`
```json
{"command": "list_formats"}
→ {"status": "ok", "formats": [{"id": "wav", "description": "Waveform Audio (PCM, lossless)"}]}
```

#### `get_recording_settings`
```json
{"command": "get_recording_settings"}
→ {"status": "ok", "settings": {"output_path": "...", "session_template": "...", "format": "wav"}}
```

#### `set_recording_settings`
Cannot be changed while recording. Accepts any subset of fields.
```json
{"command": "set_recording_settings", "output_path": "/mnt/usb/recordings"}
{"command": "set_recording_settings", "session_template": "%Y%m%d-%H%M%S"}
```

#### `play`
```json
{"command": "play", "session": "2024-01-15_20-30-00", "filename": "01-kick.wav", "offset": 0.0}
```

#### `playback_stop` / `pause` / `resume`
```json
{"command": "playback_stop"}
{"command": "pause"}
{"command": "resume"}
```

#### `seek`
```json
{"command": "seek", "seconds": 42.5}
```

---

## Adding a new mixer

1. Create `config/mixers/<id>.yaml` following the `xr18.yaml` template.
2. The `id` must appear in `arecord -l` output (case-insensitive) for auto-detection.
3. Restart the daemon.

---

## Troubleshooting

### Service won't start — missing tools

```
Missing required system tools: 'arecord' (package: alsa-utils), 'ffmpeg' (package: ffmpeg)
```
```bash
sudo apt install -y alsa-utils ffmpeg
```

### Mixer not detected

```bash
arecord -l
```
Confirm the mixer `id` appears in the output. If listed under a different name, update the `id` field in its YAML.

### Recording produces empty or corrupt files

Check the ALSA device is not held by another process:
```bash
fuser /dev/snd/*
```

Verify the device's actual capture format:
```bash
arecord -D hw:0 --dump-hw-params /dev/null
```
Update `input_format` in the mixer YAML to match.

### Output path not writable

```bash
lsblk
ls -la /mnt/usb/
```

### View live logs

```bash
sudo journalctl -u live-rec-daemon -f
```

Increase verbosity:
```bash
# edit /opt/live-rec-pi/daemon/config/daemon.yaml → log_level: DEBUG
sudo systemctl restart live-rec-daemon
```

### Stale socket after crash

```bash
rm /tmp/live-rec.sock
sudo systemctl restart live-rec-daemon
```
