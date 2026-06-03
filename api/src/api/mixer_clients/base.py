from __future__ import annotations

from abc import ABC, abstractmethod


class MixerClient(ABC):
    """Protocol client for a physical mixer.

    One concrete implementation per mixer id. Handles the mixer's native
    protocol (OSC, MIDI, HTTP, etc.) independently from the daemon's ALSA
    recording layer.
    """

    @abstractmethod
    def get_channel_state(self) -> list[dict]:
        """Return the current channel state from the physical mixer.

        Each entry: {"index": int, "name": str}
        Additional keys (fader, mute, etc.) may be added by implementations.
        """
        ...
