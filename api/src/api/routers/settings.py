from __future__ import annotations

from fastapi import APIRouter, Request
from pydantic import BaseModel

from ..daemon_client import DaemonClient
from ..dependencies import daemon_error_to_http

router = APIRouter(tags=["settings"])


class SettingsUpdate(BaseModel):
    output_path: str | None = None
    session_template: str | None = None
    format: str | None = None


@router.get("/settings")
async def get_settings(request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        return await daemon.send_async("get_recording_settings")


@router.patch("/settings")
async def update_settings(body: SettingsUpdate, request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    fields = {k: v for k, v in body.model_dump().items() if v is not None}
    if not fields:
        return await daemon.send_async("get_recording_settings")
    with daemon_error_to_http():
        return await daemon.send_async("set_recording_settings", **fields)


@router.get("/formats")
async def list_formats(request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        return await daemon.send_async("list_formats")
