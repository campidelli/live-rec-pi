#!/usr/bin/env python3
from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

import uvicorn
from fastapi import FastAPI

from .config import load_config
from .daemon_client import DaemonClient
from .routers import mixers, playback, recordings, settings, status

_CONFIG_DIR_DEFAULT = Path(__file__).parent.parent.parent.parent / "config"


def create_app(config_dir: Path) -> FastAPI:
    cfg = load_config(config_dir)

    logging.basicConfig(
        level=getattr(logging, cfg.log_level, logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    app = FastAPI(title="live-rec-pi api", version="0.1.0")
    app.state.daemon = DaemonClient(cfg.socket_path)
    app.state.config = cfg

    app.include_router(status.router)
    app.include_router(mixers.router)
    app.include_router(recordings.router)
    app.include_router(playback.router)
    app.include_router(settings.router)

    return app


def main() -> None:
    parser = argparse.ArgumentParser(description="live-rec-pi api")
    parser.add_argument(
        "--config-dir",
        type=Path,
        default=_CONFIG_DIR_DEFAULT,
        help="Path to config directory (default: <repo>/api/config)",
    )
    args = parser.parse_args()

    app = create_app(args.config_dir)
    cfg = app.state.config

    logging.getLogger(__name__).info(
        "Starting api on %s:%d", cfg.host, cfg.port
    )
    uvicorn.run(app, host=cfg.host, port=cfg.port)


if __name__ == "__main__":
    main()
