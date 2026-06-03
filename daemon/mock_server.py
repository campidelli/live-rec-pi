#!/usr/bin/env python3
"""Mock daemon for local development and Docker testing.

Speaks the same Unix socket / JSON-lines protocol as the real daemon but
requires no ALSA hardware. Recording and playback are simulated with timers.
"""
from __future__ import annotations

import json
import logging
import os
import signal
import socket
import sys
import threading
import time
from datetime import datetime
from pathlib import Path

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
log = logging.getLogger("mock-daemon")

SOCKET_PATH = os.environ.get("SOCKET_PATH", "/run/live-rec/daemon.sock")
RECORDINGS_DIR = Path(os.environ.get("RECORDINGS_DIR", "/recordings"))
RECORDINGS_DIR.mkdir(parents=True, exist_ok=True)

MOCK_CHANNELS = [
    {"index": i, "id": f"channel-{(i + 1):02d}"}
    for i in range(18)
]

MOCK_SESSIONS = [
    {
        "session": "2024-06-01_20-00-00",
        "files": [
            {"name": f"{(i + 1):02d}-channel-{(i + 1):02d}.wav", "size_bytes": 48000 * 3 * 120}
            for i in range(18)
        ],
    }
]

# Mutable runtime state
_state: dict = {
    "recording": False,
    "recording_session": None,
    "recording_files": [],
    "recording_start": None,
    "playing": False,
    "paused": False,
    "play_session": None,
    "play_file": None,
    "play_start": None,
    "play_duration": 120.0,
    "channels": list(MOCK_CHANNELS),
    "sessions": list(MOCK_SESSIONS),
}
_lock = threading.Lock()


def _build_status() -> dict:
    with _lock:
        elapsed = 0.0
        if _state["recording"] and _state["recording_start"]:
            elapsed = time.time() - _state["recording_start"]

        position = 0.0
        if _state["playing"] and _state["play_start"] and not _state["paused"]:
            position = min(time.time() - _state["play_start"], _state["play_duration"])

        return {
            "status": "recording" if _state["recording"] else "idle",
            "connected": True,
            "device": "hw:MOCK",
            "device_model": "X Air 18 (mock)",
            "mixer_id": "xr18",
            "channels": _state["channels"],
            "recording_session": _state["recording_session"],
            "recording_files": _state["recording_files"],
            "record_time": elapsed,
            "playing": _state["playing"],
            "paused": _state["paused"],
            "play_session": _state["play_session"],
            "play_file": _state["play_file"],
            "position": position,
            "duration": _state["play_duration"] if _state["playing"] else 0.0,
            "sessions": _state["sessions"],
            "storage_path": str(RECORDINGS_DIR),
            "storage_free_bytes": 32 * 1024 ** 3,
            "storage_total_bytes": 64 * 1024 ** 3,
        }


def _dispatch(msg: dict) -> dict:
    command = msg.get("command", "")

    if command == "status":
        return _build_status()

    if command == "start":
        with _lock:
            if _state["recording"]:
                return {"status": "error", "message": "Recording already running"}
            session = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
            files = [
                str(RECORDINGS_DIR / session / f"{(i + 1):02d}-{ch['id']}.wav")
                for i, ch in enumerate(_state["channels"])
            ]
            _state.update({
                "recording": True,
                "recording_session": session,
                "recording_files": files,
                "recording_start": time.time(),
            })
        return {"status": "ok", "session": session}

    if command == "stop":
        with _lock:
            if not _state["recording"]:
                return {"status": "error", "message": "already idle"}
            session = _state["recording_session"]
            files = [
                {"name": Path(f).name, "size_bytes": int(_state.get("record_time", 0) * 48000 * 3)}
                for f in _state["recording_files"]
            ]
            _state["sessions"].append({"session": session, "files": files})
            _state.update({
                "recording": False,
                "recording_session": None,
                "recording_files": [],
                "recording_start": None,
            })
        return {"status": "ok"}

    if command == "list_mixers":
        return {"status": "ok", "mixers": [{"id": "xr18", "name": "X Air 18", "active": True}]}

    if command == "load_mixer":
        mixer_id = msg.get("id")
        if mixer_id != "xr18":
            return {"status": "error", "message": f"Unknown mixer id: {mixer_id!r}"}
        return {"status": "ok", "mixer": mixer_id}

    if command == "list_formats":
        return {"status": "ok", "formats": [{"id": "wav", "description": "Waveform Audio (PCM, lossless)"}]}

    if command == "get_channel_ids":
        with _lock:
            return {"status": "ok", "channels": _state["channels"]}

    if command == "set_channel_id":
        index = msg.get("index")
        channel_id = msg.get("channel_id")
        if index is None or channel_id is None:
            return {"status": "error", "message": "missing fields: index, channel_id"}
        with _lock:
            if index < 0 or index >= len(_state["channels"]):
                return {"status": "error", "message": f"Channel index {index} out of range"}
            _state["channels"][index]["id"] = channel_id
            return {"status": "ok", "channels": _state["channels"]}

    if command == "get_recording_settings":
        return {"status": "ok", "settings": {
            "output_path": str(RECORDINGS_DIR),
            "session_template": "%Y-%m-%d_%H-%M-%S",
            "format": "wav",
        }}

    if command == "set_recording_settings":
        return {"status": "ok", "settings": {
            "output_path": str(RECORDINGS_DIR),
            "session_template": msg.get("session_template", "%Y-%m-%d_%H-%M-%S"),
            "format": msg.get("format", "wav"),
        }}

    if command == "play":
        with _lock:
            _state.update({
                "playing": True,
                "paused": False,
                "play_session": msg.get("session"),
                "play_file": msg.get("filename"),
                "play_start": time.time() - float(msg.get("offset", 0)),
            })
        return {"status": "ok"}

    if command == "playback_stop":
        with _lock:
            _state.update({"playing": False, "paused": False, "play_session": None,
                           "play_file": None, "play_start": None})
        return {"status": "ok"}

    if command == "pause":
        with _lock:
            _state["paused"] = True
        return {"status": "ok"}

    if command == "resume":
        with _lock:
            _state["paused"] = False
        return {"status": "ok"}

    if command == "seek":
        with _lock:
            _state["play_start"] = time.time() - float(msg.get("seconds", 0))
        return {"status": "ok"}

    return {"status": "error", "message": f"unknown command: {command!r}"}


def _handle(conn: socket.socket) -> None:
    try:
        with conn, conn.makefile("rwb", buffering=0) as f:
            raw = f.readline()
            if not raw:
                return
            try:
                msg = json.loads(raw.decode())
            except json.JSONDecodeError:
                f.write((json.dumps({"status": "error", "message": "invalid JSON"}) + "\n").encode())
                return
            response = _dispatch(msg)
            f.write((json.dumps(response) + "\n").encode())
    except Exception:
        log.exception("Error handling connection")


def main() -> None:
    sock_path = Path(SOCKET_PATH)
    sock_path.parent.mkdir(parents=True, exist_ok=True)
    if sock_path.exists():
        sock_path.unlink()

    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind(str(sock_path))
    server.listen(5)
    server.settimeout(1.0)
    log.info("Mock daemon listening on %s", SOCKET_PATH)

    stop = threading.Event()

    def _shutdown(signum, frame):
        log.info("Shutting down")
        stop.set()

    signal.signal(signal.SIGTERM, _shutdown)
    signal.signal(signal.SIGINT, _shutdown)

    while not stop.is_set():
        try:
            conn, _ = server.accept()
        except socket.timeout:
            continue
        except OSError:
            break
        threading.Thread(target=_handle, args=(conn,), daemon=True).start()

    server.close()
    if sock_path.exists():
        sock_path.unlink()


if __name__ == "__main__":
    main()
