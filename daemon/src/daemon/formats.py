from __future__ import annotations

from enum import Enum


class OutputFormat(str, Enum):
    WAV = "wav"

    def describe(self) -> dict:
        descriptions = {
            OutputFormat.WAV: "Waveform Audio (PCM, lossless)",
        }
        return {"id": self.value, "description": descriptions[self]}
