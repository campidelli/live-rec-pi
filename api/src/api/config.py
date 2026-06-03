from __future__ import annotations

import logging
from dataclasses import dataclass
from pathlib import Path

import yaml

log = logging.getLogger(__name__)

_REQUIRED = {"socket_path", "host", "port", "log_level"}


@dataclass
class ApiConfig:
    socket_path: str
    host: str
    port: int
    log_level: str


def load_config(config_dir: Path) -> ApiConfig:
    path = config_dir / "api.yaml"
    try:
        with path.open() as f:
            raw = yaml.safe_load(f) or {}
    except FileNotFoundError:
        raise FileNotFoundError(f"Config file not found: {path}")
    except yaml.YAMLError as e:
        raise ValueError(f"Invalid YAML in {path}: {e}")

    missing = _REQUIRED - raw.keys()
    if missing:
        raise ValueError(f"Missing keys in {path}: {', '.join(sorted(missing))}")

    cfg = ApiConfig(
        socket_path=raw["socket_path"],
        host=raw["host"],
        port=int(raw["port"]),
        log_level=raw["log_level"].upper(),
    )
    log.debug("Loaded api config: %s", cfg)
    return cfg
