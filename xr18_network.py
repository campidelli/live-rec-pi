
import ipaddress
import logging
import re
import select
import socket
import struct
import subprocess
import time

try:
    import psutil
except ImportError:  # psutil is optional; fall back to `ip addr`
    psutil = None


# ============================================================
# XR18 NETWORK DISCOVERY
# ============================================================

XR18_PORT = 10024
XR18_DISCOVERY_TIMEOUT = 3.0
XR18_INCLUDE_LIMITED_BROADCAST = True

logger = logging.getLogger("xr18.discovery")


# ============================================================
# OSC ENCODING
# ============================================================

def osc_encode_string(value: str) -> bytes:
    """Encode an OSC string with null termination and 4-byte padding."""

    data = value.encode("utf-8") + b"\0"
    padding = (4 - len(data) % 4) % 4

    return data + (b"\0" * padding)


def osc_xinfo_request() -> bytes:
    """Create an OSC /xinfo request with no arguments."""

    return (
        osc_encode_string("/xinfo")
        + osc_encode_string(",")
    )


# ============================================================
# OSC DECODING
# ============================================================

def osc_decode_string(data: bytes, offset: int):
    """Decode one padded OSC string."""

    end = data.find(b"\0", offset)

    if end < 0:
        raise ValueError("OSC string has no null terminator")

    value = data[offset:end].decode("utf-8")

    next_offset = (end + 4) & ~3

    if next_offset > len(data):
        raise ValueError("Invalid OSC string padding")

    return value, next_offset


def osc_decode_message(data: bytes):
    """Decode an OSC message containing string arguments."""

    address, offset = osc_decode_string(data, 0)
    type_tags, offset = osc_decode_string(data, offset)

    if not type_tags.startswith(","):
        raise ValueError("Invalid OSC type tag")

    arguments = []

    for tag in type_tags[1:]:

        if tag == "s":
            value, offset = osc_decode_string(data, offset)
            arguments.append(value)

        elif tag == "i":
            if offset + 4 > len(data):
                raise ValueError("Incomplete OSC integer")

            value = struct.unpack_from(">i", data, offset)[0]
            arguments.append(value)
            offset += 4

        elif tag == "f":
            if offset + 4 > len(data):
                raise ValueError("Incomplete OSC float")

            value = struct.unpack_from(">f", data, offset)[0]
            arguments.append(value)
            offset += 4

        else:
            raise ValueError(f"Unsupported OSC type tag: {tag}")

    return address, arguments


# ============================================================
# NETWORK INTERFACE DISCOVERY
# ============================================================

def _get_ipv4_interfaces_ip_cmd():
    """Fallback using `ip -o -4 addr show` (iproute2, always on Armbian)."""

    interfaces = []

    try:
        out = subprocess.run(
            ["ip", "-o", "-4", "addr", "show", "up"],
            capture_output=True, text=True, timeout=5, check=False
        ).stdout
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return interfaces

    for line in out.splitlines():
        match = re.match(r"\d+:\s+(\S+)\s+inet\s+(\d+\.\d+\.\d+\.\d+/\d+)", line)
        if not match:
            continue

        name, cidr = match.groups()
        iface = ipaddress.ip_interface(cidr)

        if iface.ip.is_loopback or iface.network.prefixlen >= 31:
            continue

        interfaces.append({
            "name": name.split("@")[0],
            "ip": str(iface.ip),
            "netmask": str(iface.netmask),
            "network": str(iface.network),
            "broadcast": str(iface.network.broadcast_address)
        })

    return interfaces


def get_ipv4_interfaces():
    """
    Return active IPv4 interfaces and their broadcast addresses.

    Virtual interfaces are intentionally included.
    Loopback interfaces are excluded.
    """

    if psutil is None:
        return _get_ipv4_interfaces_ip_cmd()

    interfaces = []

    addresses = psutil.net_if_addrs()
    statistics = psutil.net_if_stats()

    for interface_name, interface_addresses in addresses.items():

        stats = statistics.get(interface_name)

        if stats is None or not stats.isup:
            continue

        for address in interface_addresses:

            if address.family != socket.AF_INET:
                continue

            try:
                local_ip = ipaddress.IPv4Address(address.address)

                if local_ip.is_loopback or local_ip.is_unspecified:
                    continue

                if local_ip.is_multicast:
                    continue

                if not address.netmask:
                    logger.warning(
                        "Skipping interface without IPv4 netmask: %s",
                        interface_name
                    )
                    continue

                network = ipaddress.IPv4Network(
                    f"{address.address}/{address.netmask}",
                    strict=False
                )

                # /31 and /32 do not have usable directed broadcasts.
                if network.prefixlen >= 31:
                    continue

                broadcast = (
                    address.broadcast
                    or str(network.broadcast_address)
                )

                interfaces.append({
                    "name": interface_name,
                    "ip": str(local_ip),
                    "netmask": address.netmask,
                    "network": str(network),
                    "broadcast": broadcast
                })

            except (ValueError, TypeError) as exc:
                logger.warning(
                    "Invalid IPv4 configuration on %s: %s",
                    interface_name,
                    exc
                )

    return interfaces


# ============================================================
# XR18 RESPONSE VALIDATION
# ============================================================

def validate_xr18_response(data: bytes, sender_ip: str):
    """
    Validate an XR18 /xinfo response.

    Expected arguments:
        IP address
        Mixer name
        Model
        Firmware
    """

    address, arguments = osc_decode_message(data)

    if address != "/xinfo":
        return None

    if len(arguments) < 4:
        return None

    if not all(isinstance(arg, str) for arg in arguments[:4]):
        return None

    reported_ip, mixer_name, model, firmware = arguments[:4]

    # Do not accept arbitrary OSC devices (XR18 / X18 / MR18 family).
    if not any(m in model.upper() for m in ("XR18", "X18", "MR18")):
        return None

    if not mixer_name.strip():
        return None

    try:
        reported_ip = str(ipaddress.IPv4Address(reported_ip))
    except ValueError:
        return None

    return {
        "ip": sender_ip,
        "reported_ip": reported_ip,
        "name": mixer_name,
        "model": model,
        "firmware": firmware
    }


# ============================================================
# UDP DISCOVERY
# ============================================================

def discover_xr18(
    port=XR18_PORT,
    timeout=XR18_DISCOVERY_TIMEOUT,
    extra_targets=None
):
    """
    extra_targets: optional list of unicast IPs to probe in addition to
    broadcasts (useful when the router blocks broadcast traffic).

    Discover all XR18 mixers on locally connected IPv4 networks.

    Each interface gets its own UDP socket bound to its local IP.
    This avoids relying on the operating system to select the
    correct outgoing interface for broadcast traffic.

    Returns a list of discovered mixers.
    """

    interfaces = get_ipv4_interfaces()

    if not interfaces:
        logger.warning("No suitable IPv4 interfaces found")
        return []

    logger.info("Starting XR18 discovery")
    logger.info("Interfaces detected: %d", len(interfaces))

    for interface in interfaces:
        logger.info(
            "Interface %s: IP=%s Network=%s Broadcast=%s",
            interface["name"],
            interface["ip"],
            interface["network"],
            interface["broadcast"]
        )

    request = osc_xinfo_request()

    # Each socket is bound to a particular interface.
    sockets = []

    # Keyed by sender IP to avoid duplicate responses.
    discovered = {}

    try:
        for interface in interfaces:

            sock = socket.socket(
                socket.AF_INET,
                socket.SOCK_DGRAM
            )

            try:
                sock.setsockopt(
                    socket.SOL_SOCKET,
                    socket.SO_BROADCAST,
                    1
                )

                sock.bind((interface["ip"], 0))

                targets = {
                    interface["broadcast"]
                }

                if XR18_INCLUDE_LIMITED_BROADCAST:
                    targets.add("255.255.255.255")

                for extra in extra_targets or []:
                    try:
                        if ipaddress.IPv4Address(extra) in ipaddress.IPv4Network(
                            interface["network"]
                        ):
                            targets.add(extra)
                    except ValueError:
                        pass

                for target in targets:

                    try:
                        sock.sendto(request, (target, port))

                        logger.info(
                            "Sent /xinfo via %s to %s:%d",
                            interface["name"],
                            target,
                            port
                        )

                    except OSError as exc:
                        logger.warning(
                            "Broadcast failed: interface=%s target=%s error=%s",
                            interface["name"],
                            target,
                            exc
                        )

                sockets.append(sock)

            except OSError as exc:
                logger.warning(
                    "Could not initialize interface %s: %s",
                    interface["name"],
                    exc
                )
                sock.close()

        if not sockets:
            logger.error("No UDP discovery sockets could be initialized")
            return []

        # Fixed overall discovery deadline.
        deadline = time.monotonic() + timeout

        while True:

            remaining = deadline - time.monotonic()

            if remaining <= 0:
                break

            try:
                readable, _, _ = select.select(
                    sockets,
                    [],
                    [],
                    remaining
                )

            except OSError as exc:
                logger.error("UDP socket selection failed: %s", exc)
                break

            if not readable:
                break

            for sock in readable:

                try:
                    data, sender = sock.recvfrom(2048)
                    sender_ip = sender[0]

                    mixer = validate_xr18_response(
                        data,
                        sender_ip
                    )

                    if mixer is None:
                        logger.debug(
                            "Ignored non-XR18 response from %s",
                            sender_ip
                        )
                        continue

                    # Sender IP is authoritative for the current
                    # network connection. The reported IP is retained
                    # for diagnostics.
                    if mixer["ip"] in discovered:
                        continue

                    mixer["port"] = port

                    discovered[mixer["ip"]] = mixer

                    logger.info(
                        "XR18 FOUND: %s (%s) at %s:%d firmware=%s",
                        mixer["name"],
                        mixer["model"],
                        mixer["ip"],
                        port,
                        mixer["firmware"]
                    )

                except (OSError, ValueError) as exc:
                    logger.warning(
                        "Failed to process UDP response: %s",
                        exc
                    )

    finally:
        for sock in sockets:
            sock.close()

    results = list(discovered.values())

    logger.info(
        "Discovery completed. Found %d XR18 mixer(s).",
        len(results)
    )

    return results


# ============================================================
# STANDALONE TEST
# ============================================================

if __name__ == "__main__":

    logging.basicConfig(
        level=logging.INFO,
        format="[%(levelname)s] %(message)s"
    )

    mixers = discover_xr18()

    for mixer in mixers:
        print()
        print("XR18 DISCOVERED")
        print(f"  Name:     {mixer['name']}")
        print(f"  Model:    {mixer['model']}")
        print(f"  IP:       {mixer['ip']}")
        print(f"  Firmware: {mixer['firmware']}")
