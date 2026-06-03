from __future__ import annotations

from fastapi import APIRouter, Request

from ..daemon_client import DaemonClient
from ..dependencies import daemon_error_to_http

router = APIRouter(prefix="/recordings", tags=["recordings"])


@router.get("")
async def list_recordings(request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        resp = await daemon.send_async("status")
    return {"sessions": resp.get("sessions", [])}


@router.post("/start")
async def start_recording(request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        return await daemon.send_async("start")


@router.post("/stop")
async def stop_recording(request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        return await daemon.send_async("stop")
