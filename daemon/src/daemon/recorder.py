from __future__ import annotations

import logging
import os
import signal
import subprocess
import time
from datetime import datetime
from pathlib import Path

from .mixer import Mixer
from .storage import Storage

log = logging.getLogger(__name__)


class Recorder:
    """Manages a single ffmpeg recording process. One session at a time."""

    def __init__(self, mixer: Mixer, storage: Storage) -> None:
        self._mixer = mixer
        self._storage = storage
        self._proc: subprocess.Popen | None = None
        self.session: str | None = None
        self.files: list[str] = []
        self._start_time: float | None = None

    @property
    def is_recording(self) -> bool:
        return self._proc is not None

    def elapsed(self) -> float:
        if self._proc is None or self._start_time is None:
            return 0.0
        return time.time() - self._start_time

    def start(self) -> str:
        if self._proc:
            raise RuntimeError("Recording already running")
        if not self._mixer.is_connected():
            raise RuntimeError("Mixer not connected")

        if not self._mixer.is_loaded:
            raise RuntimeError("No mixer loaded")

        settings = self._storage.get_settings()
        fmt = settings["format"]
        session_name = datetime.now().strftime(settings["session_template"])
        session_dir = self._storage.session_dir(session_name)
        session_dir.mkdir(parents=True, exist_ok=True)

        channel_ids = self._mixer.channel_ids
        output_paths = [
            session_dir / f"{(i + 1):02d}-{ch}.{fmt}"
            for i, ch in enumerate(channel_ids)
        ]

        cmd = self._build_cmd(session_dir, channel_ids, fmt)

        log.info("Recording started: session=%s channels=%d", session_name, len(channel_ids))
        self._proc = subprocess.Popen(cmd, preexec_fn=os.setsid)
        self.session = session_name
        self.files = [str(p) for p in output_paths]
        self._start_time = time.time()
        return session_name

    def _build_cmd(self, session_dir: Path, channel_ids: list[str], fmt: str) -> list[str]:
        cmd = [
            "ffmpeg",
            "-loglevel", "error",
            "-f", "alsa",
            "-acodec", self._mixer.input_format,
            "-ac", str(self._mixer.channels),
            "-ar", str(self._mixer.sample_rate),
            "-i", self._mixer.device,
        ]

        for i, ch in enumerate(channel_ids):
            cmd += ["-map_channel", f"0.0.{i}"]
            cmd += ["-c:a", self._mixer.output_codec]
            cmd += [str(session_dir / f"{(i + 1):02d}-{ch}.{fmt}")]

        return cmd

    def stop(self) -> None:
        if not self._proc:
            return
        log.info("Recording stopped: session=%s", self.session)
        os.killpg(os.getpgid(self._proc.pid), signal.SIGTERM)
        self._proc.wait()
        self._proc = None
        self.session = None
        self.files = []
        self._start_time = None
