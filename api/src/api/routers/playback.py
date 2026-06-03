from __future__ import annotations

from fastapi import APIRouter, Request
from pydantic import BaseModel

from ..daemon_client import DaemonClient
from ..dependencies import daemon_error_to_http

router = APIRouter(prefix="/playback", tags=["playback"])


class PlayRequest(BaseModel):
    session: str
    filename: str
    offset: float = 0.0


class SeekRequest(BaseModel):
    seconds: float


@router.post("/play")
async def play(body: PlayRequest, request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        return await daemon.send_async(
            "play", session=body.session, filename=body.filename, offset=body.offset
        )


@router.post("/stop")
async def stop(request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        return await daemon.send_async("playback_stop")


@router.post("/pause")
async def pause(request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        return await daemon.send_async("pause")


@router.post("/resume")
async def resume(request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        return await daemon.send_async("resume")


@router.post("/seek")
async def seek(body: SeekRequest, request: Request) -> dict:
    daemon: DaemonClient = request.app.state.daemon
    with daemon_error_to_http():
        return await daemon.send_async("seek", seconds=body.seconds)
