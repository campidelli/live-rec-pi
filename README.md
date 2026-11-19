# XR18 Recorder, Hardware Diagnostics & Network Discovery

A multitrack recorder for the Behringer XR18, plus utilities for detecting the XR18 USB audio interface, discovering XR18 mixers over the network using OSC, and identifying USB storage devices.

- `xr18_recorder.py`: records each XR18 USB channel to its own WAV file on a pen drive.
- `xr18_diagnostics.py`: checks that the mixer, its USB audio and the pen drive are all visible.
- `xr18_network.py`: OSC network discovery used by both.

Designed for Orange Pi running Armbian (Debian/Ubuntu-based Linux).

## Requirements

- Python 3
- Behringer XR18 connected via USB (for audio detection)
- Network connection to the XR18 (for OSC discovery)

## Installation

Update the system:

```bash
sudo apt update
```

Install the required packages:

```bash
sudo apt install -y \
    python3 \
    python3-sounddevice \
    python3-psutil \
    libportaudio2 \
    alsa-utils \
    iproute2 \
    util-linux
```

These packages provide:

| Package | Purpose |
|---|---|
| python3 | Python runtime |
| python3-sounddevice | USB audio device detection |
| python3-psutil | Network interface discovery |
| libportaudio2 | Audio backend required by sounddevice |
| alsa-utils | Audio device diagnostics |
| iproute2 | Network interface information |
| util-linux | USB storage detection (`lsblk`) |

No additional pip installation is required.

`python3-sounddevice`, `libportaudio2` and `python3-psutil` are now optional: the diagnostics use `arecord`, `lsblk`, `lsusb` and `ip` instead (install `usbutils` for `lsusb`).

## Running

Run hardware diagnostics (three checks plus a PASS/WARN/FAIL summary):

```bash
python3 xr18_diagnostics.py
python3 xr18_diagnostics.py --ip 192.168.1.50   # also probe a known mixer IP
python3 xr18_diagnostics.py --no-write-test     # don't write a temp file to the pen drive
python3 xr18_diagnostics.py --min-free-gb 20    # warn when free space is below 20 GB
```

1. **Network**: sends OSC `/xinfo` and reports the mixer name, model, IP and firmware.
2. **Audio**: finds the XR18 through ALSA and lists its USB capture and playback channels.
3. **Storage**: finds the USB pen drive and reports filesystem, mount point and free space. It also runs a write test and estimates recording hours. It warns about FAT (4 GB file limit).

Only drives on the USB bus are inspected. The SD card / eMMC (`mmcblk*`) and any disk holding a system mount (`/`, `/boot`, `/var/log`...) are never listed or touched.

If the pen drive is not mounted, add `--mount` (optionally `--mountpoint /mnt/recordings`). The tool mounts it with your uid/gid, so exFAT/FAT/NTFS drives are writable without root. Run it as `sudo python3 xr18_diagnostics.py --mount`, or let it call `sudo -n` (passwordless sudo needed). exFAT also needs `sudo apt install exfatprogs`.

The exit code is 1 if any check fails. If audio access is denied, run `sudo usermod -aG audio $USER` and log in again.

Run standalone XR18 network discovery:

```bash
python3 xr18_network.py
```

## Recording

```bash
sudo python3 xr18_recorder.py
python3 xr18_recorder.py --debug            # also show debug messages on the console
python3 xr18_recorder.py --duration 60      # stop automatically after 60 seconds
python3 xr18_recorder.py --ip 192.168.1.139 # also probe a known mixer IP
```

Run it with `sudo` (or as a service, see below) so it can mount the pen drive if needed.

**Minimum requirements.** The XR18 must be connected via USB and a USB pen drive must be present. If either is missing the script logs an error and exits with code 2 before creating anything. The SD card / eMMC is never used.

**Output.** Each run creates a folder on the pen drive named after the start time, for example `recording_2026-11-19-180227`, containing:

- one mono 24-bit, 48 kHz WAV file per channel
- `recording.log`, a debug log of the session

**File names.** The script also tries to find the mixer on the network. If it does, it reads each channel's name and colour from the mixer:

- A channel named "voz bruno" is saved as `08-voz-bruno.wav` (lowercase, accents removed, spaces become dashes).
- Channels whose colour is OFF (0) are not recorded. Use the absence of colour on the mixer to keep unused channels out of the recording.
- If a channel's colour can't be read, it is recorded anyway and a warning is logged.

If the mixer is not found on the network, every channel is recorded with a generic name: `01-channel.wav`, `02-channel.wav`, and so on.

USB channels 17 and 18 are read from the Main L/R bus (the default USB routing). If you routed the USB sends differently, their names and colours may not match.

**Safety.** WAV headers are refreshed and synced to the drive every 5 seconds, so files stay playable if power is lost (at most the last few seconds are lost). Every log line is synced as it is written. Stop with Ctrl+C or SIGTERM and the files are finalized and synced before the script exits.

## Start recording at boot (systemd)

The `systemd/xr18-recorder.service` unit starts the recorder automatically when the Pi boots. From the repository folder on the Pi, install it with:

```bash
sed "s|@REPO_DIR@|$PWD|g" systemd/xr18-recorder.service | sudo tee /etc/systemd/system/xr18-recorder.service
sudo systemctl daemon-reload
sudo systemctl enable --now xr18-recorder
```

The `sed` command fills in the path of the repository, so no manual editing is needed. Run it again if you move the repository.

How it behaves:

- **Runs as root**, which the script needs to mount the pen drive.
- **Waits for the mixer and the drive.** If the mixer's USB or the pen drive is not ready, the script exits with code 2 before writing anything, and systemd retries every 10 seconds. You can power on the mixer or plug in the drive after the Pi has booted.
- **Network.** The service waits for the network to come up so the mixer's names and colours can be read. If the network is still not available, all channels are recorded with generic names.
- **A new recording starts on every boot**, and also on every automatic retry after a failure, such as the audio capture dying mid-session. Each one gets its own folder.
- **Stopping.** `sudo systemctl stop xr18-recorder` stops the recording cleanly. Wait for the command to return before unplugging the drive. A manual stop is not restarted.

Useful commands:

```bash
systemctl status xr18-recorder      # is it running?
journalctl -u xr18-recorder -f      # live service output (also shows errors before the folder exists)
sudo systemctl disable --now xr18-recorder   # stop it and don't start at boot
```

The main log for a session is `recording.log` on the pen drive. Use `journalctl` for problems that happen before the recording folder exists, such as a missing drive.

## USB Audio Verification

Check whether Linux detects the XR18:

```bash
lsusb
```

List available ALSA recording devices:

```bash
arecord -l
```

## Network Discovery

The network discovery implementation:

- Detects active IPv4 network interfaces.
- Supports multiple network interfaces.
- Calculates broadcast addresses dynamically.
- Sends OSC `/xinfo` requests.
- Validates responses to identify genuine XR18 mixers.
- Supports dynamically assigned IP addresses (DHCP).
- Does not require a predefined XR18 IP address.

OSC discovery uses UDP port `10024`.

## Notes

All OSC encoding, decoding and network communication use Python's standard library.

The scripts do not require any external OSC library.
