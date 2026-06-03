from __future__ import annotations

import threading
from abc import ABC, abstractmethod
from typing import Callable


class MixerClient(ABC):
    """Protocol client for a physical mixer.

    One concrete implementation per mixer id. Handles the mixer's native
    protocol (OSC, MIDI, HTTP, etc.) independently from the daemon's ALSA
    recording layer.
    """

    @classmethod
    @abstractmethod
    def discover(cls) -> str | None:
        """Scan the local network for this mixer type.

        Returns the IP address of the first mixer found, or None if not found.
        Should complete within a few seconds.
        """
        ...

    @abstractmethod
    def get_channel_state(self) -> list[dict]:
        """Return the current channel state from the physical mixer.

        Each entry: {"index": int, "name": str}
        Additional keys (fader, mute, etc.) may be added by implementations.
        """
        ...

    @abstractmethod
    def stream_meters(
        self,
        callback: Callable[[list[dict]], None],
        stop_event: threading.Event,
    ) -> None:
        """Stream live meter levels until stop_event is set.

        Calls callback with a list of {"index": int, "level": float} dicts
        whenever new meter data arrives. level is 0.0 (silence) to 1.0 (peak).
        Blocks until stop_event is set; must clean up its socket on return.
        """
        ...
