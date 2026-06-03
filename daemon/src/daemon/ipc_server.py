from __future__ import annotations

import json
import logging
import os
import socket
import threading

from .formats import OutputFormat
from .mixer import Mixer
from .playback import Playback
from .recorder import Recorder
from .storage import Storage

log = logging.getLogger(__name__)

class IpcServer(threading.Thread):
    def __init__(
        self,
        mixer: Mixer,
        recorder: Recorder,
        playback: Playback,
        storage: Storage,
        socket_path: str,
    ) -> None:
        super().__init__(daemon=True, name="ipc-server")
        self._mixer = mixer
        self._recorder = recorder
        self._playback = playback
        self._storage = storage
        self.socket_path = socket_path
        self._stop_event = threading.Event()
        self._sock: socket.socket | None = None

    def run(self) -> None:
        if os.path.exists(self.socket_path):
            os.unlink(self.socket_path)

        self._sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self._sock.bind(self.socket_path)
        self._sock.listen(5)
        self._sock.settimeout(1.0)
        log.info("IPC server listening on %s", self.socket_path)

        while not self._stop_event.is_set():
            try:
                conn, _ = self._sock.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            threading.Thread(
                target=self._handle,
                args=(conn,),
                daemon=True,
                name="ipc-conn",
            ).start()

        self._cleanup()

    def _handle(self, conn: socket.socket) -> None:
        try:
            with conn, conn.makefile("rwb", buffering=0) as f:
                raw = f.readline()
                if not raw:
                    return
                try:
                    msg = json.loads(raw.decode())
                except json.JSONDecodeError:
                    self._send(f, {"status": "error", "message": "invalid JSON"})
                    return
                response = self._dispatch(msg.get("command", ""), msg)
                self._send(f, response)
        except Exception:
            log.exception("Error handling IPC connection")

    def _dispatch(self, command: str, msg: dict) -> dict:
        try:
            if command == "start":
                session = self._recorder.start()
                return {"status": "ok", "session": session}

            if command == "stop":
                if not self._recorder.is_recording:
                    return {"status": "error", "message": "already idle"}
                self._recorder.stop()
                return {"status": "ok"}

            if command == "status":
                return self._build_status()

            if command == "list_mixers":
                return {"status": "ok", "mixers": self._mixer.list_mixers()}

            if command == "list_formats":
                return {"status": "ok", "formats": [f.describe() for f in OutputFormat]}

            if command == "get_recording_settings":
                return {"status": "ok", "settings": self._storage.get_settings()}

            if command == "set_recording_settings":
                if self._recorder.is_recording:
                    return {"status": "error", "message": "cannot change settings while recording"}
                fields = {k: v for k, v in msg.items() if k != "command"}
                if not fields:
                    return {"status": "error", "message": "no settings provided"}
                self._storage.update_settings(**fields)
                return {"status": "ok", "settings": self._storage.get_settings()}

            if command == "load_mixer":
                mixer_id = msg.get("id")
                if not mixer_id:
                    return {"status": "error", "message": "missing field: id"}
                self._recorder.stop()
                self._playback.stop()
                self._mixer.load(mixer_id)
                return {"status": "ok", "mixer": mixer_id}

            if command == "get_channel_ids":
                return {"status": "ok", "channels": self._mixer.get_channel_ids()}

            if command == "set_channel_id":
                index = msg.get("index")
                channel_id = msg.get("channel_id")
                if index is None or channel_id is None:
                    return {"status": "error", "message": "missing fields: index, channel_id"}
                self._mixer.set_channel_id(int(index), channel_id)
                return {"status": "ok", "channels": self._mixer.get_channel_ids()}

            return {"status": "error", "message": f"unknown command: {command!r}"}

        except (RuntimeError, ValueError) as e:
            return {"status": "error", "message": str(e)}
        except Exception:
            log.exception("Unhandled error dispatching command %r", command)
            return {"status": "error", "message": "internal error"}

    def _build_status(self) -> dict:
        connected = self._mixer.is_connected() or self._recorder.is_recording
        storage = self._storage.free_space()
        return {
            "status": "recording" if self._recorder.is_recording else "idle",
            "connected": connected,
            "device": self._mixer.device,
            "device_model": self._mixer.device_model,
            "mixer_id": self._mixer.id,
            "channels": self._mixer.get_channel_ids(),
            "recording_session": self._recorder.session,
            "recording_files": self._recorder.files,
            "record_time": self._recorder.elapsed(),
            "playing": self._playback.is_playing,
            "paused": self._playback.is_paused,
            "play_session": self._playback.session,
            "play_file": self._playback.file,
            "position": self._playback.position(),
            "duration": self._playback.duration,
            "sessions": self._storage.list_sessions(),
            "storage_path": str(self._storage.recordings_dir),
            "storage_free_bytes": storage["free_bytes"],
            "storage_total_bytes": storage["total_bytes"],
        }

    @staticmethod
    def _send(f, data: dict) -> None:
        f.write((json.dumps(data, default=str) + "\n").encode())

    def shutdown(self) -> None:
        self._stop_event.set()
        if self._sock:
            try:
                self._sock.close()
            except OSError:
                pass

    def _cleanup(self) -> None:
        try:
            os.unlink(self.socket_path)
        except OSError:
            pass
        log.info("IPC server stopped")
