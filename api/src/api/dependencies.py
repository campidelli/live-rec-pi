from __future__ import annotations

import asyncio
from contextlib import contextmanager

from fastapi import HTTPException, Request

from .daemon_client import DaemonClient, DaemonError


async def get_daemon(request: Request) -> DaemonClient:
    return request.app.state.daemon


@contextmanager
def daemon_error_to_http():
    """Convert DaemonError into an appropriate HTTP error response."""
    try:
        yield
    except DaemonError as e:
        msg = str(e)
        if "not found" in msg or "not running" in msg:
            raise HTTPException(status_code=503, detail=msg)
        raise HTTPException(status_code=502, detail=msg)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
