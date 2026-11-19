
#!/usr/bin/env python3

import json
import platform
import plistlib
import re
import socket
import struct
import subprocess
import sys
import time
import ipaddress

try:
    import sounddevice as sd
except ImportError:
    sd = None


# ============================================================
# CONFIGURATION
# ============================================================

XR18_IP = None # "192.168.1.50"  # Set to None to disable direct discovery
XR18_PORT = 10024
XR18_TIMEOUT = 3

XR18_USB_NAMES = ("XR18", "X AIR", "BEHRINGER")


# ============================================================
# GENERAL UTILITIES
# ============================================================

def log(section, message):
    print(f"[{section}] {message}", flush=True)


def run_command(command):
    try:
        return subprocess.run(
            command,
            capture_output=True,
            text=True,
            timeout=10,
            check=False
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return None


# ============================================================
# USB AUDIO
# ============================================================

def detect_xr18_audio():
    log("USB AUDIO", "Scanning audio devices...")

    if sd is None:
        log("USB AUDIO", "sounddevice is not installed.")
        return

    try:
        devices = sd.query_devices()
    except Exception as exc:
        log("USB AUDIO", f"Could not enumerate audio devices: {exc}")
        return

    found = False

    for index, device in enumerate(devices):
        name = device["name"]
        max_inputs = device["max_input_channels"]
        max_outputs = device["max_output_channels"]

        if not any(n in name.upper() for n in XR18_USB_NAMES):
            continue

        found = True

        log("USB AUDIO", f"XR18 detected: {name}")
        log("USB AUDIO", f"Audio device index: {index}")
        log("USB AUDIO", f"Input channels: {max_inputs}")
        log("USB AUDIO", f"Output channels: {max_outputs}")
        log("USB AUDIO", f"Default sample rate: {device['default_samplerate']} Hz")

        if max_inputs:
            log("USB AUDIO", "Available recording channels:")
            for channel in range(1, max_inputs + 1):
                log("USB AUDIO", f"  Input {channel:02d}")

        if max_outputs:
            log("USB AUDIO", "Available playback channels:")
            for channel in range(1, max_outputs + 1):
                log("USB AUDIO", f"  Output {channel:02d}")

    if not found:
        log("USB AUDIO", "XR18 not found in audio devices.")
        log("USB AUDIO", "Check USB cable and mixer power.")


# ============================================================
# OSC UTILITIES
# ============================================================

def osc_encode_string(value):
    data = value.encode("utf-8") + b"\0"
    return data + b"\0" * ((-len(data)) % 4)


def osc_message(address):
    return osc_encode_string(address) + osc_encode_string(",")


def osc_read_string(packet, offset):
    end = packet.find(b"\0", offset)

    if end == -1:
        raise ValueError("Invalid OSC string")

    value = packet[offset:end].decode("utf-8", errors="replace")
    next_offset = (end + 4) & ~3

    return value, next_offset


def osc_decode(packet):
    try:
        address, offset = osc_read_string(packet, 0)

        if address == "#bundle":
            return {
                "address": address,
                "raw": packet.hex()
            }

        tags, offset = osc_read_string(packet, offset)
        args = []

        for tag in tags[1:]:

            if tag == "s":
                value, offset = osc_read_string(packet, offset)
                args.append(value)

            elif tag == "i":
                value = struct.unpack_from(">i", packet, offset)[0]
                offset += 4
                args.append(value)

            elif tag == "f":
                value = struct.unpack_from(">f", packet, offset)[0]
                offset += 4
                args.append(value)

            elif tag == "h":
                value = struct.unpack_from(">q", packet, offset)[0]
                offset += 8
                args.append(value)

            elif tag == "d":
                value = struct.unpack_from(">d", packet, offset)[0]
                offset += 8
                args.append(value)

            elif tag == "T":
                args.append(True)

            elif tag == "F":
                args.append(False)

            elif tag == "N":
                args.append(None)

            elif tag == "b":
                size = struct.unpack_from(">i", packet, offset)[0]
                offset += 4
                args.append(packet[offset:offset + size].hex())
                offset += (size + 3) & ~3

            else:
                args.append(f"<unsupported:{tag}>")
                break

        return {
            "address": address,
            "arguments": args
        }

    except Exception as exc:
        return {
            "decode_error": str(exc),
            "raw": packet.hex()
        }


# ============================================================
# NETWORK DISCOVERY
# ============================================================

def get_broadcast_addresses():
    addresses = set()

    system = platform.system()

    if system == "Linux":

        result = run_command(["ip", "-o", "-4", "addr", "show"])

        if result:
            for line in result.stdout.splitlines():

                match = re.search(
                    r"inet\s+(\d+\.\d+\.\d+\.\d+/\d+)",
                    line
                )

                if not match:
                    continue

                interface = ipaddress.ip_interface(match.group(1))

                if (
                    interface.ip.is_loopback
                    or interface.ip.is_link_local
                    or interface.network.prefixlen >= 31
                ):
                    continue

                addresses.add(
                    str(interface.network.broadcast_address)
                )

    elif system == "Darwin":

        result = run_command(["ifconfig"])

        if result:
            for line in result.stdout.splitlines():

                match = re.search(
                    r"inet\s+(\d+\.\d+\.\d+\.\d+)"
                    r".*?netmask\s+(0x[0-9a-fA-F]+)",
                    line
                )

                if not match:
                    continue

                ip = match.group(1)
                mask = int(match.group(2), 16)
                netmask = str(ipaddress.IPv4Address(mask))

                interface = ipaddress.ip_interface(
                    f"{ip}/{netmask}"
                )

                if (
                    interface.ip.is_loopback
                    or interface.ip.is_link_local
                    or interface.network.prefixlen >= 31
                ):
                    continue

                addresses.add(
                    str(interface.network.broadcast_address)
                )

    return addresses


def discover_xr18():
    log("NETWORK", "Searching for XR18 mixers...")
    log("NETWORK", f"OSC port: {XR18_PORT}")

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    sock.setsockopt(
        socket.SOL_SOCKET,
        socket.SO_BROADCAST,
        1
    )

    sock.setsockopt(
        socket.SOL_SOCKET,
        socket.SO_REUSEADDR,
        1
    )

    discovered = {}

    try:
        sock.bind(("", 0))

        local_port = sock.getsockname()[1]

        log("NETWORK", f"Local UDP port: {local_port}")

        packet = osc_message("/xinfo")

        # ----------------------------------------------------
        # 1. DIRECT DISCOVERY
        # ----------------------------------------------------

        if XR18_IP:

            log("NETWORK", f"Trying direct discovery: {XR18_IP}")

            try:
                sock.sendto(
                    packet,
                    (XR18_IP, XR18_PORT)
                )

                log(
                    "NETWORK",
                    f"Sent /xinfo to {XR18_IP}:{XR18_PORT}"
                )

            except OSError as exc:
                log("NETWORK", f"Direct request failed: {exc}")

        # ----------------------------------------------------
        # 2. BROADCAST DISCOVERY
        # ----------------------------------------------------

        broadcasts = get_broadcast_addresses()

        log(
            "NETWORK",
            f"Broadcast addresses: {', '.join(sorted(broadcasts)) or 'None'}"
        )

        for address in sorted(broadcasts):

            try:
                sock.sendto(
                    packet,
                    (address, XR18_PORT)
                )

                log(
                    "NETWORK",
                    f"Sent /xinfo to {address}:{XR18_PORT}"
                )

            except OSError as exc:
                log(
                    "NETWORK",
                    f"Could not send to {address}: {exc}"
                )

        # ----------------------------------------------------
        # 3. RECEIVE RESPONSES
        # ----------------------------------------------------

        deadline = time.monotonic() + XR18_TIMEOUT

        while time.monotonic() < deadline:

            remaining = deadline - time.monotonic()
            sock.settimeout(min(0.5, max(0.01, remaining)))

            try:
                data, sender = sock.recvfrom(65535)

            except socket.timeout:
                continue

            decoded = osc_decode(data)

            log(
                "NETWORK",
                f"Received OSC packet from {sender}: {decoded}"
            )

            if decoded.get("address") != "/xinfo":
                continue

            ip = sender[0]

            if ip in discovered:
                continue

            discovered[ip] = decoded

            log("NETWORK", "=" * 40)
            log("NETWORK", "XR18 FOUND!")
            log("NETWORK", f"Mixer IP: {ip}")
            log("NETWORK", f"Mixer UDP port: {sender[1]}")
            log("NETWORK", f"OSC response: {decoded}")
            log("NETWORK", "=" * 40)

        if not discovered:
            log("NETWORK", "No XR18 response received.")
            log("NETWORK", "Check OSC connectivity and mixer configuration.")

        return discovered

    except Exception as exc:
        log("NETWORK", f"Discovery error: {exc}")
        return {}

    finally:
        sock.close()


# ============================================================
# USB STORAGE - macOS
# ============================================================

def detect_usb_macos():

    result = run_command([
        "diskutil",
        "list",
        "-plist"
    ])

    if not result or result.returncode != 0:
        log("STORAGE", "Could not execute diskutil.")
        return

    try:
        data = plistlib.loads(result.stdout.encode())

    except Exception as exc:
        log("STORAGE", f"Could not parse diskutil: {exc}")
        return

    found = False

    for disk in data.get("AllDisksAndPartitions", []):

        device = disk.get("DeviceIdentifier")

        if not device:
            continue

        info_result = run_command([
            "diskutil",
            "info",
            "-plist",
            f"/dev/{device}"
        ])

        if not info_result or info_result.returncode != 0:
            continue

        try:
            info = plistlib.loads(info_result.stdout.encode())

        except Exception:
            continue

        if info.get("Internal", True):
            continue

        protocol = info.get("BusProtocol", "")
        removable = info.get("RemovableMedia", False)

        if protocol.upper() != "USB" and not removable:
            continue

        found = True

        media_name = info.get("MediaName", "Unknown")
        capacity = info.get("TotalSize", 0)

        log("STORAGE", "=" * 40)
        log("STORAGE", "USB STORAGE DETECTED")
        log("STORAGE", f"Device: /dev/{device}")
        log("STORAGE", f"Physical name: {media_name}")
        log("STORAGE", f"Protocol: {protocol}")
        log("STORAGE", f"Capacity: {capacity / (1024 ** 3):.2f} GB")

        partitions = disk.get("Partitions", [])

        if not partitions:
            log("STORAGE", "No partitions found.")

        for partition in partitions:

            partition_name = partition.get("DeviceIdentifier")

            if not partition_name:
                continue

            partition_result = run_command([
                "diskutil",
                "info",
                "-plist",
                f"/dev/{partition_name}"
            ])

            if not partition_result or partition_result.returncode != 0:
                continue

            try:
                partition_info = plistlib.loads(
                    partition_result.stdout.encode()
                )

            except Exception:
                continue

            volume_name = partition_info.get("VolumeName")
            filesystem = partition_info.get("FilesystemName")
            mount_point = partition_info.get("MountPoint")

            log("STORAGE", f"Partition: {partition_name}")
            log("STORAGE", f"Volume label: {volume_name or 'Not available'}")
            log("STORAGE", f"Filesystem: {filesystem or 'Unknown'}")
            log("STORAGE", f"Mount point: {mount_point or 'Not mounted'}")

    if not found:
        log("STORAGE", "No USB storage devices found.")


# ============================================================
# USB STORAGE - Linux
# ============================================================

def detect_usb_linux():

    result = run_command([
        "lsblk",
        "-J",
        "-b",
        "-o",
        "NAME,TRAN,RM,SIZE,FSTYPE,LABEL,MODEL,TYPE,MOUNTPOINTS"
    ])

    if not result or result.returncode != 0:
        log("STORAGE", "Could not execute lsblk.")
        return

    try:
        data = json.loads(result.stdout)

    except Exception as exc:
        log("STORAGE", f"Could not parse lsblk: {exc}")
        return

    found = False

    def inspect(device):

        nonlocal found

        if (
            device.get("type") == "disk"
            and (
                device.get("tran") == "usb"
                or device.get("rm") is True
            )
        ):

            found = True

            name = device.get("model") or device.get("name")
            size = int(device.get("size") or 0)

            log("STORAGE", "=" * 40)
            log("STORAGE", "USB STORAGE DETECTED")
            log("STORAGE", f"Physical name: {name}")
            log("STORAGE", f"Device: /dev/{device['name']}")
            log("STORAGE", f"Capacity: {size / (1024 ** 3):.2f} GB")

            for partition in device.get("children", []):

                mount_points = partition.get("mountpoints") or []

                log(
                    "STORAGE",
                    f"Partition: /dev/{partition['name']}"
                )

                log(
                    "STORAGE",
                    f"Volume label: {partition.get('label') or 'Not available'}"
                )

                log(
                    "STORAGE",
                    f"Filesystem: {partition.get('fstype') or 'Unknown'}"
                )

                log(
                    "STORAGE",
                    f"Mount point: {', '.join(p for p in mount_points if p) or 'Not mounted'}"
                )

    for device in data.get("blockdevices", []):
        inspect(device)

    if not found:
        log("STORAGE", "No USB storage devices found.")


def detect_usb_storage():

    log("STORAGE", "Scanning storage devices...")

    system = platform.system()

    if system == "Darwin":
        detect_usb_macos()

    elif system == "Linux":
        detect_usb_linux()

    else:
        log("STORAGE", f"Unsupported operating system: {system}")


# ============================================================
# MAIN
# ============================================================

def main():

    print("\n" + "=" * 60)
    print(" XR18 / PI RECORDER - HARDWARE DIAGNOSTICS")
    print("=" * 60)

    log("SYSTEM", f"OS: {platform.system()} {platform.release()}")
    log("SYSTEM", f"Architecture: {platform.machine()}")
    log("SYSTEM", f"Python: {sys.version.split()[0]}")

    print("\n--- USB AUDIO ---")
    detect_xr18_audio()

    print("\n--- NETWORK / OSC ---")
    mixers = discover_xr18()

    print("\n--- USB STORAGE ---")
    detect_usb_storage()

    print("\n" + "=" * 60)

    log("SYSTEM", f"Discovered mixers: {len(mixers)}")
    log("SYSTEM", "Diagnostics completed.")

    print("=" * 60 + "\n")


if __name__ == "__main__":
    main()
