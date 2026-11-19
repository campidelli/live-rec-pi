#!/usr/bin/env python3
"""
XR18 multitrack recorder for Orange Pi (Armbian).

Records every XR18 USB channel to its own mono WAV file on the USB pen drive.

Hard requirements (otherwise it exits with code 2):
  * the XR18 is connected via USB (ALSA capture device)
  * a USB pen drive is connected (it is mounted automatically if needed)

Optional, best effort:
  * the XR18 is found on the network (OSC). If so, channel names and colours
    are read from the mixer: files are named "08-voz-bruno.wav" and channels
    whose colour is 0 (OFF) are NOT recorded. Without the mixer, all channels
    are recorded as "01-channel.wav", "02-channel.wav", ...

Output:
    <pendrive>/recording_YYYY-MM-DD-HHMMSS/
        01-kick.wav ...
        recording.log          (debug log)

Stop with Ctrl+C (or SIGTERM). WAV headers are refreshed every few seconds,
so files stay playable even if the Pi loses power.

Usage:
    python3 xr18_recorder.py
    python3 xr18_recorder.py --debug            # debug output on console too
    python3 xr18_recorder.py --duration 60      # stop after 60 s
    python3 xr18_recorder.py --ip 192.168.1.139 # also probe this mixer IP
"""

import argparse
import datetime
import logging
import logging.handlers
import os
import queue
import re
import select
import shutil
import signal
import socket
import struct
import subprocess
import sys
import threading
import time
import unicodedata

import xr18_diagnostics as diag
import xr18_network


# ============================================================
# CONFIGURATION
# ============================================================

DEFAULT_MOUNTPOINT = "/mnt/recordings"
DEFAULT_SAMPLE_RATE = 48000

# ALSA sample formats we can handle: name -> bytes per sample in the container.
# Preference order. 32-bit containers are stored as 24-bit in the WAV files.
SUPPORTED_FORMATS = (("S32_LE", 4), ("S24_3LE", 3), ("S16_LE", 2))

CHUNK_FRAMES = 4800            # 100 ms of audio per read at 48 kHz
SYNC_INTERVAL = 5.0            # seconds between WAV header refresh + fsync
STATUS_INTERVAL = 10.0         # seconds between status log lines

OSC_QUERY_ATTEMPTS = 3
OSC_QUERY_WAIT = 1.0           # seconds to wait for replies per attempt

# Which OSC node holds name/colour for each USB channel.
# Channels 1-16 are the mixer input channels. Channels 17-18 are assumed to be
# the Main L/R bus (XR18 default USB send routing). Edit here if you routed the
# USB sends differently.
OSC_FALLBACK_PREFIX = "/lr/config"
USB_DEFAULT_LABELS = {17: "main-l", 18: "main-r"}

FILESYSTEMS_WITHOUT_PERMISSIONS = ("exfat", "vfat", "fat", "ntfs", "ntfs3")

log = logging.getLogger("recorder")


# ============================================================
# LOGGING
# ============================================================

LOG_FORMAT = "%(asctime)s.%(msecs)03d %(levelname)-7s [%(name)s] %(message)s"
LOG_DATEFMT = "%Y-%m-%d %H:%M:%S"


def setup_logging(debug_console):
    """
    Console logging now; the file handler is attached once the recording
    folder exists. Everything logged before that is buffered and replayed
    into recording.log.
    """

    root = logging.getLogger()
    root.setLevel(logging.DEBUG)

    formatter = logging.Formatter(LOG_FORMAT, LOG_DATEFMT)

    console = logging.StreamHandler()
    console.setLevel(logging.DEBUG if debug_console else logging.INFO)
    console.setFormatter(formatter)
    root.addHandler(console)

    buffer = logging.handlers.MemoryHandler(
        capacity=10000, flushLevel=logging.CRITICAL + 10
    )
    root.addHandler(buffer)

    return formatter, buffer


class SyncFileHandler(logging.FileHandler):
    """FileHandler that fsyncs after every record, so no log line is lost
    if the Pi loses power or the pen drive is pulled (exFAT keeps unsynced
    data in cache). Log volume is low, so the cost is negligible."""

    def emit(self, record):
        super().emit(record)
        try:
            if self.stream:
                self.stream.flush()
                os.fsync(self.stream.fileno())
        except OSError:
            pass  # never let a logging failure stop the recording


def attach_log_file(path, formatter, buffer):
    handler = SyncFileHandler(path, encoding="utf-8")
    handler.setLevel(logging.DEBUG)
    handler.setFormatter(formatter)

    buffer.setTarget(handler)
    buffer.flush()

    root = logging.getLogger()
    root.removeHandler(buffer)
    root.addHandler(handler)

    return handler


# ============================================================
# REQUIREMENT 1: XR18 USB AUDIO
# ============================================================

def find_mixer_usb():
    """
    Locate the XR18 as an ALSA capture device.
    Returns dict(card, device, card_name, channels, rate, format, container)
    or None.
    """

    captures = diag.parse_alsa_cards("arecord")

    if captures is None:
        log.error("arecord not found. Install: sudo apt install alsa-utils")
        return None

    log.debug("ALSA capture devices: %s", captures)

    entry = None
    for candidate in captures:
        label = (f"{candidate['card_id']} {candidate['card_name']} "
                 f"{candidate['device_name']}").upper()
        if any(name in label for name in diag.XR18_USB_NAMES):
            entry = candidate
            break

    if entry is None:
        log.error("XR18 not found on USB (no matching ALSA capture device). "
                  "Check the USB cable and mixer power.")
        return None

    card, device = entry["card"], entry["device"]
    log.info("XR18 USB audio: card %d \"%s\" (hw:%d,%d)",
             card, entry["card_name"], card, device)

    result = diag.run_command(
        ["arecord", "-D", f"hw:{card},{device}", "--dump-hw-params",
         "-d", "1", "/dev/null"], timeout=10
    )
    text = (result.stdout + result.stderr) if result else ""
    log.debug("ALSA hw params:\n%s", text.strip())

    channels = None
    match = re.search(r"^CHANNELS:\s*\[?\s*(\d+)(?:\s+(\d+))?", text, re.M)
    if match:
        channels = int(match.group(2) or match.group(1))
    else:
        channels = diag.proc_stream_channels(card).get("capture")

    if not channels:
        log.error("Could not determine the XR18 capture channel count.")
        return None

    rate = DEFAULT_SAMPLE_RATE
    match = re.search(r"^RATE:\s*(.+)$", text, re.M)
    if match:
        rates = [int(n) for n in re.findall(r"\d+", match.group(1))]
        if rates and DEFAULT_SAMPLE_RATE not in rates:
            rate = rates[0]

    formats = []
    match = re.search(r"^FORMAT:\s*(.+)$", text, re.M)
    if match:
        formats = re.findall(r"S\d+_(?:3)?LE", match.group(1))

    chosen = None
    for name, container in SUPPORTED_FORMATS:
        if not formats or name in formats:
            chosen = (name, container)
            break

    if chosen is None:
        log.error("No supported sample format. Device offers: %s", formats)
        return None

    log.info("Capture: %d channels, %d Hz, ALSA format %s",
             channels, rate, chosen[0])

    return {
        "card": card,
        "device": device,
        "card_name": entry["card_name"],
        "channels": channels,
        "rate": rate,
        "format": chosen[0],
        "container": chosen[1],
    }


# ============================================================
# REQUIREMENT 2: USB PEN DRIVE
# ============================================================

def find_pendrive(mountpoint):
    """
    Return the mount point of a writable USB pen drive volume, mounting it
    if needed. Only disks on the USB bus are considered: the SD card /
    eMMC holding the OS is never touched.
    """

    if shutil.which("lsblk") is None:
        log.error("lsblk not found. Install: sudo apt install util-linux")
        return None

    data = diag._lsblk()
    if data is None:
        log.error("Could not read block devices with lsblk.")
        return None

    log.debug("lsblk: %s", data)

    for disk in diag._usb_disks(data):
        model = (disk.get("model") or disk["name"]).strip()
        log.info("USB drive: %s (/dev/%s, %s)",
                 model, disk["name"], diag.human_size(disk.get("size")))

        for volume in disk.get("children") or [disk]:
            path = volume.get("path") or f"/dev/{volume['name']}"
            fstype = volume.get("fstype")
            mounts = diag._mountpoints(volume)

            log.debug("Volume %s fs=%s label=%s mounts=%s",
                      path, fstype, volume.get("label"), mounts)

            if not fstype:
                log.warning("%s has no filesystem, skipping.", path)
                continue

            mount = mounts[0] if mounts else None

            if mount is None:
                log.info("%s is not mounted; mounting at %s", path, mountpoint)
                mount = diag.mount_volume(path, fstype, mountpoint)

            elif (not diag._can_write(mount)[0]
                    and fstype.lower() in FILESYSTEMS_WITHOUT_PERMISSIONS):
                log.info("%s mounted at %s but not writable; remounting "
                         "with your uid/gid", path, mount)
                mount = diag.mount_volume(path, fstype, mountpoint, remount=True)

            if not mount:
                log.warning("Could not mount %s.", path)
                continue

            writable, error = diag._can_write(mount)
            if not writable:
                log.warning("%s is not writable: %s", mount, error)
                continue

            usage = shutil.disk_usage(mount)
            log.info("Pen drive ready: %s (%s, %s free)",
                     mount, fstype, diag.human_size(usage.free))
            return mount

    log.error("No usable USB pen drive found. Plug one in and try again.")
    return None


# ============================================================
# OPTIONAL: CHANNEL NAMES AND COLOURS FROM THE MIXER (OSC)
# ============================================================

def osc_query(address):
    return (xr18_network.osc_encode_string(address)
            + xr18_network.osc_encode_string(","))


def osc_node_for_channel(number):
    """OSC config node (without /name or /color) for a USB channel."""
    if number <= 16:
        return f"/ch/{number:02d}/config"
    return OSC_FALLBACK_PREFIX


def query_mixer(ip, usb_channels, port=xr18_network.XR18_PORT):
    """
    Read name and colour of every USB channel from the mixer.
    Returns {channel_number: {"name": str|None, "color": int|None}}.
    """

    addresses = set()
    for number in range(1, usb_channels + 1):
        node = osc_node_for_channel(number)
        addresses.add(f"{node}/name")
        addresses.add(f"{node}/color")

    values = {}
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    try:
        for attempt in range(1, OSC_QUERY_ATTEMPTS + 1):
            missing = sorted(addresses - set(values))
            if not missing:
                break

            log.debug("OSC query attempt %d: %d values missing",
                      attempt, len(missing))

            for address in missing:
                sock.sendto(osc_query(address), (ip, port))

            deadline = time.monotonic() + OSC_QUERY_WAIT

            while time.monotonic() < deadline and addresses - set(values):
                ready, _, _ = select.select(
                    [sock], [], [], max(0.0, deadline - time.monotonic())
                )
                if not ready:
                    break

                data, sender = sock.recvfrom(2048)

                try:
                    address, arguments = xr18_network.osc_decode_message(data)
                except ValueError as exc:
                    log.debug("Undecodable OSC from %s: %s", sender, exc)
                    continue

                log.debug("OSC reply %s %s", address, arguments)

                if address in addresses and arguments:
                    values[address] = arguments[0]
    finally:
        sock.close()

    info = {}
    for number in range(1, usb_channels + 1):
        node = osc_node_for_channel(number)
        name = values.get(f"{node}/name")
        color = values.get(f"{node}/color")
        info[number] = {
            "name": name if isinstance(name, str) else None,
            "color": color if isinstance(color, int) else None,
        }

    return info


def discover_mixer_info(usb_channels, extra_ip):
    """Return per-channel info dict, or None if the mixer is not reachable."""

    log.info("Looking for the XR18 on the network...")

    try:
        mixers = xr18_network.discover_xr18(
            extra_targets=[extra_ip] if extra_ip else None
        )
    except Exception:
        log.exception("Network discovery failed")
        return None

    if not mixers:
        log.warning("XR18 not found on the network: recording ALL channels "
                    "with generic names.")
        return None

    mixer = mixers[0]
    if len(mixers) > 1:
        log.warning("%d mixers answered, using %s", len(mixers), mixer["ip"])

    log.info("Mixer on network: %s (%s) at %s, firmware %s",
             mixer["name"], mixer["model"], mixer["ip"], mixer["firmware"])

    try:
        info = query_mixer(mixer["ip"], usb_channels)
    except OSError:
        log.exception("Could not query the mixer")
        return None

    for number, entry in info.items():
        log.debug("Mixer channel %02d: %s", number, entry)

    return info


# ============================================================
# CHANNEL PLAN
# ============================================================

def slugify(text):
    text = unicodedata.normalize("NFKD", text)
    text = text.encode("ascii", "ignore").decode("ascii").lower()
    return re.sub(r"[^a-z0-9]+", "-", text).strip("-")


def build_plan(usb_channels, mixer_info):
    """
    Return list of (channel_number, file_name) to record.

    No mixer info          -> every channel, generic names.
    Colour known and 0     -> skipped.
    Name/colour unknown    -> recorded (we cannot tell it is unused).
    """

    plan = []

    for number in range(1, usb_channels + 1):
        default_label = USB_DEFAULT_LABELS.get(number, "channel")
        label = default_label

        if mixer_info is not None:
            entry = mixer_info.get(number) or {}
            color = entry.get("color")
            name = entry.get("name")

            if color is None:
                log.warning("Channel %02d: colour unknown, recording anyway.",
                            number)
            elif color <= 0:
                log.info("Channel %02d: no colour, skipped.", number)
                continue

            if name:
                label = slugify(name) or default_label
            else:
                log.debug("Channel %02d: no name, using '%s'", number, label)

        file_name = f"{number:02d}-{label}.wav"
        log.info("Channel %02d -> %s", number, file_name)
        plan.append((number, file_name))

    return plan


# ============================================================
# WAV WRITER
# ============================================================

class WavWriter:
    """
    Mono PCM WAV file with a header that can be refreshed while recording,
    so a power cut still leaves a playable file.
    """

    def __init__(self, path, rate, sample_bytes):
        self.path = path
        self.rate = rate
        self.sample_bytes = sample_bytes
        self.data_bytes = 0
        self.file = open(path, "wb")
        self._write_header()

    def _write_header(self):
        data = min(self.data_bytes, 0xFFFFFFFF - 36)
        block = self.sample_bytes

        self.file.write(
            b"RIFF" + struct.pack("<I", 36 + data) + b"WAVE"
            + b"fmt " + struct.pack(
                "<IHHIIHH", 16, 1, 1, self.rate,
                self.rate * block, block, block * 8
            )
            + b"data" + struct.pack("<I", data)
        )

    def write(self, payload):
        self.file.write(payload)
        self.data_bytes += len(payload)

    def sync(self):
        self.file.flush()
        self.file.seek(0)
        self._write_header()
        self.file.seek(0, os.SEEK_END)
        self.file.flush()
        os.fsync(self.file.fileno())

    def close(self):
        try:
            self.sync()
        finally:
            self.file.close()


def extract_channel(chunk, channel_index, frame_bytes, container, out_bytes):
    """
    Pull one channel out of interleaved little-endian PCM, keeping the
    most significant out_bytes bytes of each sample (S32 -> 24-bit).
    """

    frames = len(chunk) // frame_bytes
    base = channel_index * container + (container - out_bytes)
    out = bytearray(frames * out_bytes)

    for k in range(out_bytes):
        out[k::out_bytes] = chunk[base + k::frame_bytes][:frames]

    return bytes(out)


# ============================================================
# RECORDER
# ============================================================

def build_arecord_command(audio):
    return [
        "arecord",
        "-D", f"hw:{audio['card']},{audio['device']}",
        "-f", audio["format"],
        "-c", str(audio["channels"]),
        "-r", str(audio["rate"]),
        "-t", "raw",
        "--buffer-time=1000000",
    ]


def record(command, plan, folder, audio, stop, duration=None):
    """
    Run `command` (raw interleaved PCM on stdout) and split it into one WAV
    per planned channel. Returns True if the session ended cleanly.
    """

    channels = audio["channels"]
    container = audio["container"]
    out_bytes = min(container, 3)
    frame_bytes = channels * container
    chunk_bytes = CHUNK_FRAMES * frame_bytes

    log.info("Starting capture: %s", " ".join(command))
    log.debug("frame=%d bytes, chunk=%d bytes, output=%d-bit",
              frame_bytes, chunk_bytes, out_bytes * 8)

    writers = {}
    for number, file_name in plan:
        writers[number] = WavWriter(
            os.path.join(folder, file_name), audio["rate"], out_bytes
        )

    proc = subprocess.Popen(
        command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        start_new_session=True  # Ctrl+C must not hit arecord directly
    )

    chunks = queue.Queue()

    def read_audio():
        try:
            while True:
                data = proc.stdout.read(chunk_bytes)
                if not data:
                    break
                chunks.put(data)
        finally:
            chunks.put(None)

    def read_errors():
        for raw in proc.stderr:
            line = raw.decode(errors="replace").strip()
            if line:
                log.warning("arecord: %s", line)

    threading.Thread(target=read_audio, daemon=True).start()
    threading.Thread(target=read_errors, daemon=True).start()

    started = time.monotonic()
    last_sync = last_status = started
    total_frames = 0
    failed = False
    terminating = False

    try:
        while True:
            now = time.monotonic()

            if not terminating and (
                stop.is_set()
                or (duration and now - started >= duration)
            ):
                log.info("Stopping capture...")
                proc.terminate()
                terminating = True

            try:
                chunk = chunks.get(timeout=0.5)
            except queue.Empty:
                chunk = b""

            if chunk is None:
                break

            if chunk:
                frames = len(chunk) // frame_bytes
                chunk = chunk[:frames * frame_bytes]

                try:
                    for number, writer in writers.items():
                        writer.write(extract_channel(
                            chunk, number - 1, frame_bytes, container, out_bytes
                        ))
                except OSError:
                    log.exception("Write error (disk full or pen drive "
                                  "removed?). Stopping.")
                    failed = True
                    proc.terminate()
                    break

                total_frames += frames

            now = time.monotonic()

            if now - last_sync >= SYNC_INTERVAL:
                for writer in writers.values():
                    writer.sync()
                last_sync = now

            if now - last_status >= STATUS_INTERVAL:
                free = shutil.disk_usage(folder).free
                log.info("Recording %s | %d channels | queue %d | %s free",
                         format_duration(total_frames / audio["rate"]),
                         len(writers), chunks.qsize(), diag.human_size(free))
                last_status = now

    finally:
        if proc.poll() is None:
            proc.terminate()

        try:
            code = proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
            code = proc.wait()

        log.debug("arecord exit code: %s", code)

        for writer in writers.values():
            try:
                writer.close()
            except OSError:
                log.exception("Could not finalise %s", writer.path)
                failed = True

    seconds = total_frames / audio["rate"]
    log.info("Recorded %s per channel (%d frames)",
             format_duration(seconds), total_frames)

    for number, file_name in plan:
        log.debug("%s: %s", file_name,
                  diag.human_size(writers[number].data_bytes))

    if not terminating and code not in (0, None):
        log.error("arecord stopped unexpectedly (exit code %s).", code)
        failed = True

    if total_frames == 0:
        log.error("No audio was captured.")
        failed = True

    return not failed


def format_duration(seconds):
    seconds = int(seconds)
    return f"{seconds // 3600:02d}:{seconds % 3600 // 60:02d}:{seconds % 60:02d}"


# ============================================================
# MAIN
# ============================================================

def main():
    parser = argparse.ArgumentParser(description="XR18 multitrack recorder")
    parser.add_argument("--debug", action="store_true",
                        help="show debug messages on the console "
                             "(they are always saved in recording.log)")
    parser.add_argument("--duration", type=float,
                        help="stop automatically after this many seconds")
    parser.add_argument("--ip", help="XR18 IP to probe directly")
    parser.add_argument("--mountpoint", default=DEFAULT_MOUNTPOINT,
                        help=f"where to mount the pen drive if needed "
                             f"(default {DEFAULT_MOUNTPOINT})")
    args = parser.parse_args()

    formatter, buffer = setup_logging(args.debug)

    log.info("XR18 recorder starting")
    log.debug("Python %s, argv=%s", sys.version.split()[0], sys.argv)

    # ---- minimum requirements -------------------------------------------

    audio = find_mixer_usb()
    if audio is None:
        return 2

    pendrive = find_pendrive(args.mountpoint)
    if pendrive is None:
        return 2

    # ---- recording folder + log file -------------------------------------

    stamp = datetime.datetime.now().strftime("%Y-%m-%d-%H%M%S")
    folder = os.path.join(pendrive, f"recording_{stamp}")
    os.makedirs(folder)

    attach_log_file(os.path.join(folder, "recording.log"), formatter, buffer)
    log.info("Recording folder: %s", folder)

    # ---- optional: names and colours from the mixer ----------------------

    mixer_info = discover_mixer_info(audio["channels"], args.ip)
    plan = build_plan(audio["channels"], mixer_info)

    if not plan:
        log.error("Every channel has colour 0: nothing to record.")
        return 1

    # ---- record -----------------------------------------------------------

    stop = threading.Event()

    def request_stop(signum, _frame):
        log.info("Signal %s received.", signal.Signals(signum).name)
        stop.set()

    signal.signal(signal.SIGINT, request_stop)
    signal.signal(signal.SIGTERM, request_stop)

    log.info("Recording %d channel(s). Press Ctrl+C to stop.", len(plan))

    try:
        ok = record(build_arecord_command(audio), plan, folder, audio,
                    stop, args.duration)
    except Exception:
        log.exception("Unexpected error while recording")
        ok = False

    log.info("Done. Files are in %s", folder)
    logging.shutdown()

    # Push everything still cached to the pen drive, plus the folder entry.
    try:
        fd = os.open(folder, os.O_RDONLY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    except OSError:
        pass
    os.sync()

    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
