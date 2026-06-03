from __future__ import annotations

import logging
import signal
import subprocess
import time

from .mixer import Mixer
from .storage import Storage

log = logging.getLogger(__name__)


class Playback:
    """Manages a single ffmpeg playback process."""

    def __init__(self, mixer: Mixer, storage: Storage) -> None:
        self._mixer = mixer
        self._storage = storage
        self._proc: subprocess.Popen | None = None
        self.session: str | None = None
        self.file: str | None = None
        self.duration: float = 0.0
        self._start_time: float | None = None
        self._offset: float = 0.0
        self._paused: bool = False
        self._pause_started: float | None = None

    @property
    def is_playing(self) -> bool:
        self._poll()
        return self._proc is not None

    @property
    def is_paused(self) -> bool:
        return self._paused

    def position(self) -> float:
        self._poll()
        if not self._proc:
            return 0.0
        if self._paused and self._pause_started is not None:
            elapsed = self._pause_started - (self._start_time or 0)
        else:
            elapsed = time.time() - (self._start_time or time.time())
        return min(self._offset + elapsed, self.duration)

    def play(self, session: str, filename: str, offset: float = 0.0) -> None:
        path = self._storage.session_dir(session) / filename
        if not path.exists():
            raise FileNotFoundError(str(path))

        self.stop()
        self.duration = self._probe_duration(path)

        if not self._mixer.is_loaded or self._mixer.device is None:
            raise RuntimeError("Mixer not connected")
        cmd = [
            "ffmpeg",
            "-loglevel", "error",
            "-ss", str(offset),
            "-i", str(path),
            "-ac", str(self._mixer.channels),
            "-ar", str(self._mixer.sample_rate),
            "-acodec", self._mixer.input_format,
            "-f", "alsa",
            self._mixer.device,
        ]

        log.info("Playback started: %s (offset=%.1fs)", path, offset)
        self._proc = subprocess.Popen(cmd)
        self.session = session
        self.file = filename
        self._start_time = time.time()
        self._offset = offset
        self._paused = False

    def pause(self) -> None:
        if self._proc and not self._paused:
            self._proc.send_signal(signal.SIGSTOP)
            self._paused = True
            self._pause_started = time.time()

    def resume(self) -> None:
        if self._proc and self._paused:
            self._proc.send_signal(signal.SIGCONT)
            if self._pause_started is not None:
                paused_duration = time.time() - self._pause_started
                self._start_time = (self._start_time or time.time()) + paused_duration
            self._pause_started = None
            self._paused = False

    def seek(self, seconds: float) -> None:
        if not self.file or not self.session:
            return
        was_paused = self._paused
        self.play(self.session, self.file, offset=seconds)
        if was_paused:
            self.pause()

    def stop(self) -> None:
        if not self._proc:
            return
        self._proc.terminate()
        self._proc.wait()
        self._reset()

    def _poll(self) -> None:
        if self._proc and self._proc.poll() is not None:
            self._reset()

    def _reset(self) -> None:
        self._proc = None
        self.session = None
        self.file = None
        self.duration = 0.0
        self._start_time = None
        self._offset = 0.0
        self._paused = False
        self._pause_started = None

    @staticmethod
    def _probe_duration(path) -> float:
        r = subprocess.run(
            [
                "ffprobe", "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                str(path),
            ],
            capture_output=True,
            text=True,
        )
        try:
            return float(r.stdout.strip())
        except ValueError:
            return 0.0
