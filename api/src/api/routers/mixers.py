from __future__ import annotations

from fastapi import APIRouter, Request
from pydantic import BaseModel

from ..daemon_client import DaemonClient
from ..dependencies import daemon_error_to_http
from ..mixer_clients.registry import resolve, supported_ids

router = APIRouter(prefix="/mixers", tags=["mixers"])


class ChannelUpdate(BaseModel):
    channel_id: str


@router.get("")
async def list_mixers(request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        return await daemon.send_async("list_mixers")


@router.post("/{mixer_id}/load")
async def load_mixer(mixer_id: str, request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        return await daemon.send_async("load_mixer", id=mixer_id)


@router.get("/{mixer_id}/channels")
async def get_channels(mixer_id: str, request: Request, host: str | None = None) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        daemon_resp = await daemon.send_async("get_channel_ids")

    daemon_channels: list[dict] = daemon_resp.get("channels", [])

    mixer_channels: list[dict] = []
    if host and mixer_id in supported_ids():
        try:
            client = resolve(mixer_id, host=host)
            mixer_channels = await __import__("asyncio").to_thread(client.get_channel_state)
        except Exception:
            pass  # mixer unreachable — return daemon channels only

    mixer_by_index = {ch["index"]: ch for ch in mixer_channels}
    merged = [
        {**ch, "mixer_name": mixer_by_index.get(ch["index"], {}).get("name", "")}
        for ch in daemon_channels
    ]
    return {"channels": merged}


@router.patch("/{mixer_id}/channels/{index}")
async def set_channel(mixer_id: str, index: int, body: ChannelUpdate, request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        return await daemon.send_async("set_channel_id", index=index, channel_id=body.channel_id)
