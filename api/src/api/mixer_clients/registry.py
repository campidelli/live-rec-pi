from __future__ import annotations

from .base import MixerClient
from .xr18 import XR18OscClient

_registry: dict[str, type[MixerClient]] = {
    "xr18": XR18OscClient,
}


def resolve(mixer_id: str, **kwargs) -> MixerClient:
    """Instantiate the MixerClient for the given mixer id.

    kwargs are forwarded to the client constructor (e.g. host="192.168.1.x").
    Raises ValueError for unknown mixer ids.
    """
    cls = _registry.get(mixer_id)
    if cls is None:
        raise ValueError(f"No mixer client registered for id: {mixer_id!r}")
    return cls(**kwargs)


def supported_ids() -> list[str]:
    return list(_registry.keys())
