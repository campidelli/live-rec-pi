from __future__ import annotations

from fastapi import APIRouter, Request

from ..daemon_client import DaemonClient, DaemonError
from ..dependencies import daemon_error_to_http

router = APIRouter(tags=["status"])


@router.get("/status")
async def get_status(request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        return await daemon.send_async("status")
