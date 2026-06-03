from __future__ import annotations

from .base import MixerClient
from .xr18 import XR18OscClient

_registry: dict[str, type[MixerClient]] = {
    "xr18": XR18OscClient,
}


def get_class(mixer_id: str) -> type[MixerClient]:
    cls = _registry.get(mixer_id)
    if cls is None:
        raise ValueError(f"No mixer client registered for id: {mixer_id!r}")
    return cls


def resolve(mixer_id: str, **kwargs) -> MixerClient:
    """Instantiate the MixerClient for the given mixer id."""
    return get_class(mixer_id)(**kwargs)


def supported_ids() -> list[str]:
    return list(_registry.keys())
