from __future__ import annotations

import asyncio
import json
import socket


class DaemonError(Exception):
    pass


class DaemonClient:
    """Synchronous IPC client for the daemon Unix socket.

    Call via asyncio.to_thread() from async FastAPI routes to avoid
    blocking the event loop.
    """

    def __init__(self, socket_path: str) -> None:
        self.socket_path = socket_path

    def send(self, command: str, **kwargs) -> dict:
        msg = {"command": command, **kwargs}
        try:
            with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as sock:
                sock.settimeout(10.0)
                sock.connect(self.socket_path)
                sock.sendall((json.dumps(msg) + "\n").encode())
                response = self._readline(sock)
        except FileNotFoundError:
            raise DaemonError("Daemon socket not found — is the daemon running?")
        except ConnectionRefusedError:
            raise DaemonError("Daemon refused connection")
        except TimeoutError:
            raise DaemonError("Daemon did not respond in time")
        except OSError as e:
            raise DaemonError(f"Socket error: {e}")

        try:
            data = json.loads(response)
        except json.JSONDecodeError as e:
            raise DaemonError(f"Invalid JSON from daemon: {e}")

        if data.get("status") == "error":
            raise DaemonError(data.get("message", "unknown error"))

        return data

    async def send_async(self, command: str, **kwargs) -> dict:
        return await asyncio.to_thread(self.send, command, **kwargs)

    @staticmethod
    def _readline(sock: socket.socket) -> str:
        buf = b""
        while not buf.endswith(b"\n"):
            chunk = sock.recv(4096)
            if not chunk:
                break
            buf += chunk
        return buf.decode().strip()
