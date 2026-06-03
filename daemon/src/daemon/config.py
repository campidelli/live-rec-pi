from __future__ import annotations

import logging
from dataclasses import dataclass
from pathlib import Path

import yaml

log = logging.getLogger(__name__)

_REQUIRED_DAEMON = {"socket_path", "recordings_dir", "log_level"}
_REQUIRED_MIXER = {"id", "name", "channels", "sample_rate", "input_format", "output_codec"}


@dataclass
class RecordingSettings:
    output_path: str
    session_template: str
    format: str


@dataclass
class DaemonConfig:
    socket_path: str
    recordings_dir: str
    log_level: str
    recording: RecordingSettings


def _load_yaml(path: Path) -> dict:
    try:
        with path.open() as f:
            return yaml.safe_load(f) or {}
    except FileNotFoundError:
        raise FileNotFoundError(f"Config file not found: {path}")
    except yaml.YAMLError as e:
        raise ValueError(f"Invalid YAML in {path}: {e}")


def _check_keys(data: dict, required: set, source: Path) -> None:
    missing = required - data.keys()
    if missing:
        raise ValueError(f"Missing keys in {source}: {', '.join(sorted(missing))}")


def load_config(config_dir: Path) -> DaemonConfig:
    daemon_path = config_dir / "daemon.yaml"
    raw_daemon = _load_yaml(daemon_path)
    _check_keys(raw_daemon, _REQUIRED_DAEMON, daemon_path)

    raw_rec = raw_daemon.get("recording", {})
    recording = RecordingSettings(
        output_path=raw_rec.get("output_path", raw_daemon["recordings_dir"]),
        session_template=raw_rec.get("session_template", "%Y-%m-%d_%H-%M-%S"),
        format=raw_rec.get("format", "wav"),
    )

    daemon_cfg = DaemonConfig(
        socket_path=raw_daemon["socket_path"],
        recordings_dir=raw_daemon["recordings_dir"],
        log_level=raw_daemon["log_level"].upper(),
        recording=recording,
    )
    log.debug("Loaded daemon config: %s", daemon_cfg)
    return daemon_cfg


def load_mixer_yamls(config_dir: Path) -> list[dict]:
    mixers_dir = config_dir / "mixers"
    if not mixers_dir.is_dir():
        raise FileNotFoundError(f"Mixers directory not found: {mixers_dir}")

    configs = []
    for path in sorted(mixers_dir.glob("*.yaml")):
        raw = _load_yaml(path)
        _check_keys(raw, _REQUIRED_MIXER, path)
        if not isinstance(raw["channels"], list) or not raw["channels"]:
            raise ValueError(f"'channels' must be a non-empty list in {path}")
        configs.append(raw)
        log.debug("Loaded mixer yaml: %s", raw["id"])

    if not configs:
        raise ValueError(f"No mixer configs found in {mixers_dir}")

    return configs
