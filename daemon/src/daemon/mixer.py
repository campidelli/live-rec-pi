from __future__ import annotations

import logging
import subprocess

log = logging.getLogger(__name__)


class Mixer:
    """
    Tracks the active mixer. Attributes are loaded from YAML dicts directly.
    Auto-detects on is_connected(); can be switched explicitly via load().
    channel_ids is mutable at runtime without affecting the base catalog.
    """

    def __init__(self, mixer_yamls: list[dict]) -> None:
        # catalog: id → raw yaml dict
        self._catalog: dict[str, dict] = {m["id"]: m for m in mixer_yamls}
        self.device: str | None = None

        # active mixer attributes — None until a mixer is loaded or detected
        self.id: str | None = None
        self.name: str | None = None
        self.sample_rate: int | None = None
        self.input_format: str | None = None
        self.output_codec: str | None = None
        self.channel_ids: list[str] = []

    @property
    def is_loaded(self) -> bool:
        return self.id is not None

    @property
    def channels(self) -> int:
        return len(self.channel_ids)

    def _apply(self, raw: dict) -> None:
        self.id = raw["id"]
        self.name = raw["name"]
        self.sample_rate = int(raw["sample_rate"])
        self.input_format = raw["input_format"]
        self.output_codec = raw["output_codec"]
        self.channel_ids = list(raw["channels"])

    def list_mixers(self) -> list[dict]:
        return [
            {"id": m["id"], "name": m["name"], "active": m["id"] == self.id}
            for m in self._catalog.values()
        ]

    def load(self, mixer_id: str) -> None:
        if mixer_id not in self._catalog:
            raise ValueError(f"Unknown mixer id: {mixer_id!r}")
        self._apply(self._catalog[mixer_id])
        self.device = None
        log.info("Mixer loaded: %s (%s)", self.id, self.name)

    def get_channel_ids(self) -> list[dict]:
        return [{"index": i, "id": ch} for i, ch in enumerate(self.channel_ids)]

    def set_channel_id(self, index: int, channel_id: str) -> None:
        if not self.is_loaded:
            raise RuntimeError("No mixer loaded")
        if index < 0 or index >= len(self.channel_ids):
            raise ValueError(f"Channel index {index} out of range (0–{len(self.channel_ids) - 1})")
        if not channel_id.strip():
            raise ValueError("Channel id must not be empty")
        self.channel_ids[index] = channel_id

    def is_connected(self) -> bool:
        try:
            r = subprocess.run(["arecord", "-l"], capture_output=True, text=True)
            current_card: str | None = None
            for line in r.stdout.splitlines():
                upper = line.upper()
                if "CARD" in upper and ":" in upper:
                    parts = line.split()
                    try:
                        card_index = parts[1].replace(":", "")
                        current_card = f"hw:{card_index}"
                    except Exception:
                        current_card = None
                        continue
                if current_card:
                    for raw in self._catalog.values():
                        if raw["id"].upper() in upper:
                            self.device = current_card
                            if self.id != raw["id"]:
                                self._apply(raw)
                            return True
            return False
        except Exception:
            log.exception("Device detection failed")
            return False
