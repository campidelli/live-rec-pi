from __future__ import annotations

import asyncio
import math
import random
import threading

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from ..daemon_client import DaemonClient, DaemonError
from ..mixer_clients.registry import resolve

router = APIRouter(tags=["ws"])

_SIMULATED_CHANNELS = 18


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
        try:
            loop.call_soon_threadsafe(queue.put_nowait, levels)
        except asyncio.QueueFull:
            pass

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


@router.websocket("/ws/meters/demo")
async def websocket_meters_demo(websocket: WebSocket) -> None:
    """Simulated meter stream for development and Docker testing.

    Generates sine-wave levels with per-channel phase/frequency offsets and
    occasional random transient spikes so every channel moves independently.
    Pushes frames at ~50ms intervals (same rate as a real XR18).
    """
    await websocket.accept()

    # Give each channel a distinct feel
    freqs   = [0.3 + (i * 0.17) % 0.9 for i in range(_SIMULATED_CHANNELS)]
    phases  = [i * (2 * math.pi / _SIMULATED_CHANNELS) for i in range(_SIMULATED_CHANNELS)]
    # base amplitude varies per channel (some channels are quieter)
    amps    = [0.3 + (i * 0.037) % 0.55 for i in range(_SIMULATED_CHANNELS)]

    t = 0.0
    try:
        while True:
            levels = []
            for i in range(_SIMULATED_CHANNELS):
                base = (math.sin(t * freqs[i] + phases[i]) + 1) / 2  # 0..1
                level = base * amps[i]
                # occasional transient spike (~3% chance per channel per frame)
                if random.random() < 0.03:
                    level = min(1.0, level + random.uniform(0.2, 0.6))
                levels.append({"index": i, "level": round(level, 3)})

            await websocket.send_json({"meters": levels})
            await asyncio.sleep(0.05)
            t += 0.05
    except WebSocketDisconnect:
        pass
