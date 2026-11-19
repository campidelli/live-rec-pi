#!/usr/bin/env python3
"""
XR18 / Pi recorder diagnostics for Orange Pi (Armbian, Debian/Ubuntu).

Checks three things and prints a PASS/FAIL summary:
  1. NETWORK  - can the Pi see the XR18 mixer over OSC (UDP 10024)?
  2. AUDIO    - does ALSA see the XR18 USB interface, and how many
                channels can the Pi capture from it?
  3. STORAGE  - is a USB pen drive present, mounted, writable, and
                does it have free space for recordings?

Only the Python standard library plus system tools (ip, arecord, lsblk,
lsusb) are required. python3-psutil / sounddevice are NOT needed.

Usage:
    python3 xr18_diagnostics.py                 # full diagnostics
    python3 xr18_diagnostics.py --ip 192.168.1.50   # also probe this IP
    python3 xr18_diagnostics.py --no-write-test     # don't touch the drive
    python3 xr18_diagnostics.py --min-free-gb 20    # warn below 20 GB free
"""

import argparse
import glob
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import tempfile

import xr18_network


# ============================================================
# CONFIGURATION
# ============================================================

XR18_USB_NAMES = ("XR18", "X18", "X AIR", "XAIR", "BEHRINGER", "MIDAS")
BEHRINGER_USB_VENDOR_ID = "1397"
DEFAULT_MIN_FREE_GB = 10.0

# Filesystems that cannot hold long multichannel recordings (4 GB limit)
FAT_FILESYSTEMS = ("vfat", "fat", "fat16", "fat32", "msdos")

OK = "PASS"
WARN = "WARN"
FAIL = "FAIL"


# ============================================================
# UTILITIES
# ============================================================

def log(section, message):
    print(f"[{section}] {message}", flush=True)


def run_command(command, timeout=10):
    try:
        return subprocess.run(
            command, capture_output=True, text=True,
            timeout=timeout, check=False
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return None


def human_size(num_bytes):
    num_bytes = float(num_bytes or 0)
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if num_bytes < 1024 or unit == "TB":
            return f"{num_bytes:.1f} {unit}"
        num_bytes /= 1024


def section(title):
    print(f"\n--- {title} ---")


# ============================================================
# 1. NETWORK
# ============================================================

def check_network(extra_ip, timeout):
    """Return (status, summary, mixers)."""

    interfaces = xr18_network.get_ipv4_interfaces()

    if not interfaces:
        log("NETWORK", "No active IPv4 interface with an address.")
        log("NETWORK", "Plug in the Ethernet cable / join the mixer's Wi-Fi, "
                       "then check `ip -4 addr`.")
        return FAIL, "Pi has no IPv4 network address", []

    for itf in interfaces:
        log("NETWORK", f"Interface {itf['name']}: {itf['ip']} "
                       f"(network {itf['network']})")

    log("NETWORK", f"Sending OSC /xinfo on UDP {xr18_network.XR18_PORT} "
                   f"(timeout {timeout:.0f}s)...")

    mixers = xr18_network.discover_xr18(
        timeout=timeout,
        extra_targets=[extra_ip] if extra_ip else None
    )

    if not mixers:
        log("NETWORK", "No XR18 answered.")
        log("NETWORK", "Checks: mixer powered and cabled to the same LAN/switch? "
                       "Same subnet as the Pi? Router/AP blocking broadcast? "
                       "Try again with --ip <mixer-ip>.")
        return FAIL, "XR18 not visible on the network", []

    for mixer in mixers:
        log("NETWORK", f"XR18 found: {mixer['name']} ({mixer['model']}) "
                       f"at {mixer['ip']}  firmware {mixer['firmware']}")

    ips = ", ".join(m["ip"] for m in mixers)
    return OK, f"XR18 visible at {ips}", mixers


# ============================================================
# 2. USB AUDIO (ALSA)
# ============================================================

def parse_alsa_cards(tool):
    """
    Parse `arecord -l` / `aplay -l`.
    Returns list of dicts: card, card_id, card_name, device, device_name.
    """

    result = run_command([tool, "-l"])
    if result is None:
        return None

    entries = []
    pattern = re.compile(
        r"card\s+(\d+):\s+(\S+)\s+\[(.*?)\],\s+device\s+(\d+):\s+(.*?)\s+\[(.*?)\]"
    )

    for line in result.stdout.splitlines():
        match = pattern.match(line.strip())
        if match:
            card, card_id, card_name, dev, _, dev_name = match.groups()
            entries.append({
                "card": int(card),
                "card_id": card_id,
                "card_name": card_name,
                "device": int(dev),
                "device_name": dev_name,
            })

    return entries


def alsa_hw_params(tool, card, device):
    """
    Query supported channels / rates without recording anything.
    `arecord --dump-hw-params` prints the hardware capabilities and exits.
    Returns dict with 'channels' (min, max) and 'rates' (list) or None.
    """

    result = run_command(
        [tool, "-D", f"hw:{card},{device}", "--dump-hw-params",
         "-d", "1", "/dev/null"],
        timeout=10
    )

    if result is None:
        return None

    text = result.stdout + result.stderr
    params = {}

    match = re.search(r"^CHANNELS:\s*\[?\s*(\d+)(?:\s+(\d+))?\s*\]?", text, re.M)
    if match:
        low = int(match.group(1))
        high = int(match.group(2) or match.group(1))
        params["channels"] = (low, high)

    match = re.search(r"^RATE:\s*(.+)$", text, re.M)
    if match:
        params["rates"] = [int(n) for n in re.findall(r"\d+", match.group(1))]

    if not params:
        if "busy" in text.lower():
            params["busy"] = True
        else:
            return None

    return params


def proc_stream_channels(card):
    """Fallback: read /proc/asound/cardN/stream0 (works while device is busy)."""

    result = {}
    try:
        with open(f"/proc/asound/card{card}/stream0") as fh:
            text = fh.read()
    except OSError:
        return result

    for direction in ("Playback", "Capture"):
        block = re.search(rf"{direction}:(.*?)(?:\n\S|\Z)", text, re.S)
        if block:
            chans = re.findall(r"Channels:\s*(\d+)", block.group(1))
            if chans:
                result[direction.lower()] = max(int(c) for c in chans)

    return result


def check_usb_bus():
    """lsusb: confirm the Behringer/Midas USB device is on the bus."""

    result = run_command(["lsusb"])
    if result is None:
        log("AUDIO", "lsusb not installed (sudo apt install usbutils).")
        return None

    for line in result.stdout.splitlines():
        low = line.lower()
        if f"{BEHRINGER_USB_VENDOR_ID}:" in low or "behringer" in low or "xr18" in low:
            log("AUDIO", f"USB bus: {line.strip()}")
            return True

    return False


def check_audio():
    """Return (status, summary, info)."""

    on_bus = check_usb_bus()

    if os.path.isdir("/dev/snd"):
        if not any(os.access(p, os.R_OK | os.W_OK) for p in glob.glob("/dev/snd/pcm*")):
            log("AUDIO", "WARNING: no read/write access to /dev/snd. "
                         "Run: sudo usermod -aG audio $USER  (then re-login)")
    else:
        log("AUDIO", "/dev/snd missing: no ALSA sound devices at all.")

    captures = parse_alsa_cards("arecord")
    playbacks = parse_alsa_cards("aplay")

    if captures is None:
        log("AUDIO", "arecord not found. Install: sudo apt install alsa-utils")
        return FAIL, "alsa-utils missing", None

    def is_xr18(entry):
        label = f"{entry['card_id']} {entry['card_name']} {entry['device_name']}".upper()
        return any(n in label for n in XR18_USB_NAMES)

    xr_captures = [c for c in captures if is_xr18(c)]

    # Show every sound card so the user can see what else is there.
    all_cards = sorted({(c["card"], c["card_name"]) for c in captures}
                       | {(c["card"], c["card_name"]) for c in (playbacks or [])})
    if all_cards:
        log("AUDIO", "ALSA cards: " + "; ".join(f"{n}: {name}" for n, name in all_cards))
    else:
        log("AUDIO", "ALSA cards: none")

    if not xr_captures:
        if on_bus:
            log("AUDIO", "XR18 is on the USB bus but ALSA has no capture device "
                         "(snd-usb-audio driver not loaded?). Try: "
                         "sudo modprobe snd-usb-audio ; dmesg | tail")
        else:
            log("AUDIO", "XR18 not detected. Check the USB cable (use the mixer's "
                         "USB-B port), power, and try another Pi USB port.")
        return FAIL, "XR18 USB audio not found", None

    entry = xr_captures[0]
    card, device = entry["card"], entry["device"]
    log("AUDIO", f"XR18 audio: card {card} \"{entry['card_name']}\", "
                 f"device {device} -> ALSA name hw:{card},{device} "
                 f"(also hw:CARD={entry['card_id']},DEV={device})")

    params = alsa_hw_params("arecord", card, device)
    stream = proc_stream_channels(card)

    capture_ch = None
    if params and "channels" in params:
        capture_ch = params["channels"][1]
    elif "capture" in stream:
        capture_ch = stream["capture"]

    playback_ch = stream.get("playback")
    if playback_ch is None and playbacks:
        pb = [p for p in playbacks if p["card"] == card]
        if pb:
            pb_params = alsa_hw_params("aplay", card, pb[0]["device"])
            if pb_params and "channels" in pb_params:
                playback_ch = pb_params["channels"][1]

    if params and params.get("busy"):
        log("AUDIO", "Device is busy (another program is using it).")

    if params and params.get("rates"):
        rates = ", ".join(f"{r} Hz" for r in params["rates"])
        log("AUDIO", f"Supported sample rates: {rates}")

    if capture_ch:
        log("AUDIO", f"Mixer -> Pi (USB returns, recordable): {capture_ch} channels")
        # XR18: 18 channels = 16 inputs + 2 (L/R) in the default USB routing
        for ch in range(1, capture_ch + 1):
            print(f"[AUDIO]     Channel {ch:02d}  (hw:{card},{device} ch {ch})")
    else:
        log("AUDIO", "Could not determine capture channel count.")

    if playback_ch:
        log("AUDIO", f"Pi -> Mixer (playback): {playback_ch} channels")

    info = {"card": card, "device": device, "capture_channels": capture_ch,
            "playback_channels": playback_ch}

    if not capture_ch:
        return WARN, "XR18 audio found but channel count unknown", info

    if capture_ch < 18:
        log("AUDIO", f"NOTE: expected 18 channels on an XR18, got {capture_ch}.")
        return WARN, f"XR18 audio found, only {capture_ch} capture channels", info

    return OK, f"XR18 audio on hw:{card},{device}, {capture_ch} capture channels", info


# ============================================================
# 3. USB STORAGE
# ============================================================

def _lsblk():
    """Run lsblk -J; older util-linux uses MOUNTPOINT, newer MOUNTPOINTS."""

    for mp_col in ("MOUNTPOINTS", "MOUNTPOINT"):
        result = run_command([
            "lsblk", "-J", "-b", "-o",
            f"NAME,PATH,TRAN,RM,HOTPLUG,SIZE,FSTYPE,LABEL,MODEL,VENDOR,TYPE,{mp_col}"
        ])
        if result and result.returncode == 0:
            try:
                return json.loads(result.stdout)
            except ValueError:
                return None
        if result is None:
            return None  # lsblk missing

    return None


def _truthy(value):
    return value in (True, 1, "1", "true")


def _mountpoints(node):
    mps = node.get("mountpoints")
    if mps is None:
        mps = [node.get("mountpoint")]
    return [m for m in mps if m]


def _usb_disks(data):
    """Yield whole-disk nodes that are USB or removable (not the SD/eMMC)."""

    for dev in data.get("blockdevices", []):
        if dev.get("type") != "disk":
            continue
        if (dev.get("tran") == "usb"
                or _truthy(dev.get("rm"))
                or _truthy(dev.get("hotplug"))):
            yield dev


def _can_write(mount):
    """Create and delete a small file to prove the drive is writable."""

    try:
        with tempfile.NamedTemporaryFile(dir=mount, prefix=".xr18_diag_") as fh:
            fh.write(b"x" * 4096)
            fh.flush()
            os.fsync(fh.fileno())
        return True, None
    except OSError as exc:
        return False, str(exc)


def check_storage(min_free_gb, write_test):
    """Return (status, summary, info)."""

    if shutil.which("lsblk") is None:
        log("STORAGE", "lsblk not found. Install: sudo apt install util-linux")
        return FAIL, "lsblk missing", None

    data = _lsblk()
    if data is None:
        log("STORAGE", "Could not read block devices with lsblk.")
        return FAIL, "could not query block devices", None

    disks = list(_usb_disks(data))

    if not disks:
        log("STORAGE", "No USB storage detected. Plug in the pen drive "
                       "(blue USB3 port if available) and re-run.")
        return FAIL, "no USB pen drive found", None

    best = None  # (status, summary, info) of the most usable volume
    ranking = {OK: 0, WARN: 1, FAIL: 2}

    for disk in disks:
        model = " ".join(filter(None, [(disk.get("vendor") or "").strip(),
                                       (disk.get("model") or "").strip()])) \
                or disk["name"]
        log("STORAGE", f"USB drive: {model}  /dev/{disk['name']}  "
                       f"{human_size(disk.get('size'))}")

        volumes = disk.get("children") or [disk]  # unpartitioned disk is its own volume

        for vol in volumes:
            path = vol.get("path") or f"/dev/{vol['name']}"
            fstype = vol.get("fstype")
            label = vol.get("label") or "-"
            mounts = _mountpoints(vol)

            log("STORAGE", f"  Volume {path}: label={label} "
                           f"fs={fstype or 'none'} size={human_size(vol.get('size'))}")

            if not fstype:
                log("STORAGE", "    No filesystem. Format it, e.g. "
                               f"sudo mkfs.ext4 -L RECORDINGS {path}")
                status, summary = FAIL, f"{path} has no filesystem"
                result = (status, summary, None)

            elif not mounts:
                log("STORAGE", "    Not mounted. Try: "
                               f"sudo mkdir -p /mnt/recordings && sudo mount {path} /mnt/recordings")
                status, summary = FAIL, f"{path} is not mounted"
                result = (status, summary, None)

            else:
                mount = mounts[0]
                usage = shutil.disk_usage(mount)
                free_gb = usage.free / 1024 ** 3
                log("STORAGE", f"    Mounted at {mount}: "
                               f"{human_size(usage.free)} free of {human_size(usage.total)}")

                status, notes = OK, []

                if fstype.lower() in FAT_FILESYSTEMS:
                    status = WARN
                    notes.append("FAT has a 4 GB file limit; long recordings will fail "
                                 "(use ext4 or exFAT)")

                if fstype.lower() in ("ntfs", "ntfs3", "fuseblk"):
                    notes.append("NTFS works but ext4 is more reliable on the Pi")

                if free_gb < min_free_gb:
                    status = WARN
                    notes.append(f"only {free_gb:.1f} GB free "
                                 f"(< {min_free_gb:g} GB)")

                if write_test:
                    ok, err = _can_write(mount)
                    if ok:
                        log("STORAGE", "    Write test: OK")
                    else:
                        log("STORAGE", f"    Write test FAILED: {err}")
                        notes.append("not writable (read-only mount or permissions; "
                                     "try: sudo chown $USER " + mount + ")")
                        status = FAIL

                for note in notes:
                    log("STORAGE", f"    NOTE: {note}")

                # 48 kHz / 24-bit / 18 ch ~ 2.6 MB/s ~ 9.3 GB per hour
                hours = usage.free / (48000 * 3 * 18 * 3600)
                log("STORAGE", f"    Capacity for recording: ~{hours:.1f} h "
                               f"at 18ch/48kHz/24-bit")

                summary = f"{mount} ({fstype}, {human_size(usage.free)} free)"
                if notes:
                    summary += " - " + "; ".join(notes)
                result = (status, summary, {"mount": mount, "fstype": fstype,
                                            "free_bytes": usage.free})

            if best is None or ranking[result[0]] < ranking[best[0]]:
                best = result

    return best


# ============================================================
# MAIN
# ============================================================

def main():
    parser = argparse.ArgumentParser(description="XR18 / Pi recorder diagnostics")
    parser.add_argument("--ip", help="XR18 IP to probe directly (in addition to broadcast)")
    parser.add_argument("--timeout", type=float, default=3.0,
                        help="network discovery timeout in seconds (default 3)")
    parser.add_argument("--min-free-gb", type=float, default=DEFAULT_MIN_FREE_GB,
                        help=f"warn if the drive has less free space (default {DEFAULT_MIN_FREE_GB:g})")
    parser.add_argument("--no-write-test", action="store_true",
                        help="skip creating a temp file on the pen drive")
    args = parser.parse_args()

    print("\n" + "=" * 60)
    print(" XR18 / PI RECORDER - DIAGNOSTICS")
    print("=" * 60)

    log("SYSTEM", f"{platform.system()} {platform.release()} ({platform.machine()})")
    try:
        with open("/proc/device-tree/model", "rb") as fh:
            log("SYSTEM", f"Board: {fh.read().decode(errors='replace').strip(chr(0))}")
    except OSError:
        pass
    log("SYSTEM", f"Python {sys.version.split()[0]}")

    if platform.system() != "Linux":
        log("SYSTEM", "This tool targets Linux (Orange Pi / Armbian); "
                      "results on other systems are not meaningful.")

    section("NETWORK: Pi -> XR18 mixer")
    net = check_network(args.ip, args.timeout)

    section("AUDIO: XR18 USB audio outputs")
    audio = check_audio()

    section("STORAGE: USB pen drive")
    storage = check_storage(args.min_free_gb, not args.no_write_test)

    results = [("Mixer on network", net), ("Mixer USB audio", audio),
               ("Pen drive", storage)]

    print("\n" + "=" * 60)
    print(" SUMMARY")
    print("=" * 60)
    for name, (status, summary, *_rest) in results:
        print(f" [{status}] {name:<18} {summary}")
    print("=" * 60)

    failed = any(r[1][0] == FAIL for r in results)
    warned = any(r[1][0] == WARN for r in results)

    if failed:
        print(" Not ready to record. Fix the FAIL items above.\n")
        return 1
    if warned:
        print(" Ready, but review the warnings above.\n")
        return 0

    print(" All good: ready to record.\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
