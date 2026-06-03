#!/usr/bin/env python3
from __future__ import annotations

import argparse
import logging
import shutil
import signal
import sys
from pathlib import Path

from .config import load_config, load_mixer_yamls
from .mixer import Mixer
from .playback import Playback
from .recorder import Recorder
from .storage import Storage
from .ipc_server import IpcServer


def _check_dependencies(log: logging.Logger) -> None:
    required = {"arecord": "alsa-utils", "ffmpeg": "ffmpeg", "ffprobe": "ffmpeg"}
    missing = [f"{bin!r} (package: {pkg})" for bin, pkg in required.items() if not shutil.which(bin)]
    if missing:
        log.error("Missing required system tools: %s", ", ".join(missing))
        log.error("Install with: sudo apt install %s", " ".join(sorted(set(required.values()))))
        sys.exit(1)
    log.debug("All required system tools found: %s", ", ".join(required))


def main() -> None:
    parser = argparse.ArgumentParser(description="live-rec-pi daemon")
    parser.add_argument(
        "--config-dir",
        type=Path,
        default=Path(__file__).parent.parent.parent.parent / "config",
        help="Path to config directory (default: <repo>/daemon/config)",
    )
    args = parser.parse_args()

    daemon_cfg = load_config(args.config_dir)
    mixer_yamls = load_mixer_yamls(args.config_dir)

    logging.basicConfig(
        level=getattr(logging, daemon_cfg.log_level, logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    log = logging.getLogger(__name__)

    _check_dependencies(log)
    log.info("Starting live-rec daemon (%d mixer(s) supported)", len(mixer_yamls))

    mixer = Mixer(mixer_yamls)
    storage = Storage(daemon_cfg)
    recorder = Recorder(mixer, storage)
    playback = Playback(mixer, storage)
    server = IpcServer(mixer, recorder, playback, storage, daemon_cfg.socket_path)

    def _shutdown(signum, frame):
        log.info("Received signal %s, shutting down", signum)
        recorder.stop()
        server.shutdown()
        sys.exit(0)

    signal.signal(signal.SIGTERM, _shutdown)
    signal.signal(signal.SIGINT, _shutdown)

    server.start()
    log.info("Daemon ready. Socket: %s", daemon_cfg.socket_path)

    signal.pause()


if __name__ == "__main__":
    main()
