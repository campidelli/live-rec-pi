from __future__ import annotations

import logging
import socket
import struct
import threading
import time
from typing import Callable

from pythonosc.dispatcher import Dispatcher
from pythonosc.osc_server import BlockingOSCUDPServer
from pythonosc.udp_client import SimpleUDPClient

from .base import MixerClient

log = logging.getLogger(__name__)

_OSC_PORT = 10023
_CHANNEL_COUNT = 18
_QUERY_TIMEOUT = 3.0
_DISCOVER_TIMEOUT = 4.0
# /xremote subscription must be renewed every <10s; renew every 8s
_XREMOTE_INTERVAL = 8.0

# Minimal OSC /xinfo message (no arguments) hand-encoded to avoid dependency
# on a send-only client just for broadcast.
_XINFO_PACKET = (
    b"/xinfo\x00\x00"   # address, padded to 8 bytes
    b",\x00\x00\x00"    # type tag string (no args), padded to 4 bytes
)


class XR18OscClient(MixerClient):
    """Protocol client for the Behringer XR18 over OSC (UDP port 10023)."""

    def __init__(self, host: str) -> None:
        self._host = host

    # ---------- discovery ----------

    @classmethod
    def discover(cls) -> str | None:
        """Broadcast /xinfo on the local network and return the first XR18 IP found."""
        found: list[str] = []
        dispatcher = Dispatcher()

        def _on_xinfo(address: str, *args: object) -> None:
            pass  # we only need the sender address, captured via server socket

        dispatcher.map("/xinfo", _on_xinfo)

        listen_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        listen_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        listen_sock.bind(("", 0))
        listen_sock.settimeout(_DISCOVER_TIMEOUT)

        broadcast_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        broadcast_sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

        try:
            broadcast_sock.sendto(_XINFO_PACKET, ("<broadcast>", _OSC_PORT))
            deadline = time.monotonic() + _DISCOVER_TIMEOUT
            while time.monotonic() < deadline:
                try:
                    _, addr = listen_sock.recvfrom(1024)
                    found.append(addr[0])
                    break
                except socket.timeout:
                    break
        except Exception:
            log.exception("XR18 discovery failed")
        finally:
            listen_sock.close()
            broadcast_sock.close()

        return found[0] if found else None

    # ---------- channel state (one-shot) ----------

    def get_channel_state(self) -> list[dict]:
        names: dict[int, str] = {}
        dispatcher = Dispatcher()

        def _on_name(address: str, *args: object) -> None:
            try:
                index = int(address.split("/")[2]) - 1
                names[index] = str(args[0]) if args else ""
            except (IndexError, ValueError):
                pass

        for ch in range(1, _CHANNEL_COUNT + 1):
            dispatcher.map(f"/ch/{ch:02d}/config/name", _on_name)

        server = BlockingOSCUDPServer(("", 0), dispatcher)
        client = SimpleUDPClient(self._host, _OSC_PORT)

        try:
            for ch in range(1, _CHANNEL_COUNT + 1):
                client.send_message(f"/ch/{ch:02d}/config/name", [])

            deadline = time.monotonic() + _QUERY_TIMEOUT
            while len(names) < _CHANNEL_COUNT and time.monotonic() < deadline:
                server.handle_request()
        except Exception:
            log.exception("OSC get_channel_state error at %s", self._host)
        finally:
            server.server_close()

        return [{"index": i, "name": names.get(i, "")} for i in range(_CHANNEL_COUNT)]

    # ---------- meter streaming (long-lived) ----------

    def stream_meters(
        self,
        callback: Callable[[list[dict]], None],
        stop_event: threading.Event,
    ) -> None:
        """Subscribe to XR18 input meters and stream levels until stop_event is set.

        The XR18 pushes /meters/1 blobs at ~50ms intervals while subscribed.
        Each blob is a bytes argument: 4-byte int32 count followed by count×4-byte
        float32 values (linear RMS levels, 0.0–1.0).
        """
        dispatcher = Dispatcher()

        def _on_meters(address: str, *args: object) -> None:
            if not args or not isinstance(args[0], bytes):
                return
            blob = args[0]
            try:
                count = struct.unpack_from(">i", blob, 0)[0]
                floats = struct.unpack_from(f">{count}f", blob, 4)
                levels = [
                    {"index": i, "level": float(floats[i])}
                    for i in range(min(count, _CHANNEL_COUNT))
                ]
                callback(levels)
            except struct.error:
                pass

        dispatcher.map("/meters/1", _on_meters)

        server = BlockingOSCUDPServer(("", 0), dispatcher)
        server.timeout = 0.5
        client = SimpleUDPClient(self._host, _OSC_PORT)

        last_xremote = 0.0
        try:
            while not stop_event.is_set():
                now = time.monotonic()
                if now - last_xremote >= _XREMOTE_INTERVAL:
                    client.send_message("/xremote", [])
                    client.send_message("/meters/1", [0])
                    last_xremote = now
                server.handle_request()
        except Exception:
            log.exception("OSC stream_meters error at %s", self._host)
        finally:
            server.server_close()
            log.debug("Meter stream stopped for %s", self._host)


    # ---------- channel state (one-shot) ----------

    def get_channel_state(self) -> list[dict]:
        names: dict[int, str] = {}
        dispatcher = Dispatcher()

        def _on_name(address: str, *args: object) -> None:
            try:
                index = int(address.split("/")[2]) - 1
                names[index] = str(args[0]) if args else ""
            except (IndexError, ValueError):
                pass

        for ch in range(1, _CHANNEL_COUNT + 1):
            dispatcher.map(f"/ch/{ch:02d}/config/name", _on_name)

        server = BlockingOSCUDPServer(("", 0), dispatcher)
        client = SimpleUDPClient(self._host, _OSC_PORT)

        try:
            for ch in range(1, _CHANNEL_COUNT + 1):
                client.send_message(f"/ch/{ch:02d}/config/name", [])

            deadline = time.monotonic() + _QUERY_TIMEOUT
            while len(names) < _CHANNEL_COUNT and time.monotonic() < deadline:
                server.handle_request()
        except Exception:
            log.exception("OSC get_channel_state error at %s", self._host)
        finally:
            server.server_close()

        return [{"index": i, "name": names.get(i, "")} for i in range(_CHANNEL_COUNT)]

    # ---------- meter streaming (long-lived) ----------

    def stream_meters(
        self,
        callback: Callable[[list[dict]], None],
        stop_event: threading.Event,
    ) -> None:
        """Subscribe to XR18 input meters and stream levels until stop_event is set.

        The XR18 pushes /meters/1 blobs at ~50ms intervals while subscribed.
        Each blob is a bytes argument: 4-byte int32 count followed by count×4-byte
        float32 values (linear RMS levels, 0.0–1.0).
        """
        dispatcher = Dispatcher()

        def _on_meters(address: str, *args: object) -> None:
            if not args or not isinstance(args[0], bytes):
                return
            blob = args[0]
            try:
                count = struct.unpack_from(">i", blob, 0)[0]
                floats = struct.unpack_from(f">{count}f", blob, 4)
                levels = [
                    {"index": i, "level": float(floats[i])}
                    for i in range(min(count, _CHANNEL_COUNT))
                ]
                callback(levels)
            except struct.error:
                pass

        dispatcher.map("/meters/1", _on_meters)

        server = BlockingOSCUDPServer(("", 0), dispatcher)
        server.timeout = 0.5
        client = SimpleUDPClient(self._host, _OSC_PORT)

        last_xremote = 0.0
        try:
            while not stop_event.is_set():
                now = time.monotonic()
                if now - last_xremote >= _XREMOTE_INTERVAL:
                    client.send_message("/xremote", [])
                    client.send_message("/meters/1", [0])
                    last_xremote = now
                server.handle_request()
        except Exception:
            log.exception("OSC stream_meters error at %s", self._host)
        finally:
            server.server_close()
            log.debug("Meter stream stopped for %s", self._host)
