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

## Running

Run hardware diagnostics:

```bash
python3 xr18_diagnostics.py
```

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
