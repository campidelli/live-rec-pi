# XR18 Hardware Diagnostics & Network Discovery

Utilities for detecting the Behringer XR18 USB audio interface, discovering XR18 mixers over the network using OSC, and identifying USB storage devices.

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
