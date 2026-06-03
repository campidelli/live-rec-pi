from __future__ import annotations

import asyncio
import threading

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from ..daemon_client import DaemonClient, DaemonError
from ..mixer_clients.registry import resolve

router = APIRouter(tags=["ws"])


@router.websocket("/ws")
async def websocket_status(websocket: WebSocket) -> None:
    await websocket.accept()
    daemon: DaemonClient = websocket.app.state.daemon
    try:
        while True:
            try:
                status = await daemon.send_async("status")
            except DaemonError as e:
                status = {"error": str(e)}
            await websocket.send_json(status)
            await asyncio.sleep(0.3)
    except WebSocketDisconnect:
        pass


@router.websocket("/ws/meters")
async def websocket_meters(websocket: WebSocket, mixer_id: str, host: str) -> None:
    """Stream live meter levels from the physical mixer over WebSocket.

    Query params:
      mixer_id — e.g. "xr18"
      host     — IP address of the mixer on the local network

    Pushes JSON frames: {"meters": [{"index": 0, "level": 0.45}, ...]}
    """
    await websocket.accept()

    queue: asyncio.Queue[list[dict]] = asyncio.Queue(maxsize=4)
    loop = asyncio.get_event_loop()
    stop_event = threading.Event()

    def _callback(levels: list[dict]) -> None:
        # called from the meter thread — put onto the asyncio queue
        try:
            loop.call_soon_threadsafe(queue.put_nowait, levels)
        except asyncio.QueueFull:
            pass  # drop frame if consumer is behind

    try:
        client = resolve(mixer_id, host=host)
    except ValueError as e:
        await websocket.send_json({"error": str(e)})
        await websocket.close()
        return

    meter_thread = threading.Thread(
        target=client.stream_meters,
        args=(_callback, stop_event),
        daemon=True,
        name=f"meters-{mixer_id}",
    )
    meter_thread.start()

    try:
        while True:
            levels = await queue.get()
            await websocket.send_json({"meters": levels})
    except WebSocketDisconnect:
        pass
    finally:
        stop_event.set()
        meter_thread.join(timeout=3.0)
