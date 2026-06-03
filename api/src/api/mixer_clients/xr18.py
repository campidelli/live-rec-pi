from __future__ import annotations

import logging
import time

from pythonosc.dispatcher import Dispatcher
from pythonosc.osc_server import BlockingOSCUDPServer
from pythonosc.udp_client import SimpleUDPClient

from .base import MixerClient

log = logging.getLogger(__name__)

_OSC_PORT = 10023
_CHANNEL_COUNT = 18
_TIMEOUT = 3.0


class XR18OscClient(MixerClient):
    """Reads channel state from a Behringer XR18 over OSC (UDP port 10023).

    The XR18 responds to individual /ch/NN/config/name queries.
    We open a temporary listener on an ephemeral port, fire all requests,
    then collect responses until timeout or all channels answered.
    """

    def __init__(self, host: str) -> None:
        self._host = host

    def get_channel_state(self) -> list[dict]:
        names: dict[int, str] = {}
        dispatcher = Dispatcher()

        def _on_name(address: str, *args) -> None:
            # address: /ch/01/config/name
            try:
                index = int(address.split("/")[2]) - 1
                names[index] = str(args[0]) if args else ""
            except (IndexError, ValueError):
                pass

        for ch in range(1, _CHANNEL_COUNT + 1):
            dispatcher.map(f"/ch/{ch:02d}/config/name", _on_name)

        server = BlockingOSCUDPServer(("", 0), dispatcher)
        listen_port = server.server_address[1]
        client = SimpleUDPClient(self._host, _OSC_PORT)

        try:
            for ch in range(1, _CHANNEL_COUNT + 1):
                client.send_message(f"/ch/{ch:02d}/config/name", [])

            deadline = time.monotonic() + _TIMEOUT
            while len(names) < _CHANNEL_COUNT and time.monotonic() < deadline:
                server.handle_request()
        except Exception:
            log.exception("OSC communication error with XR18 at %s", self._host)
        finally:
            server.server_close()

        return [
            {"index": i, "name": names.get(i, "")}
            for i in range(_CHANNEL_COUNT)
        ]
