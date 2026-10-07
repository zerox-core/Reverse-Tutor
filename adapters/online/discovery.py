"""UDP LAN discovery responder for the online dev server.

Mobile clients broadcast a probe on the LAN and this responder answers with
every IPv4 address of the dev machine, so the app can recover from DHCP drift
without hard-coded IPs.
"""

from __future__ import annotations

import json
import socket
import threading

DISCOVERY_PORT = 8137
DISCOVERY_MAGIC = "REVERSE_TUTOR_DISCOVER_V1"


class DiscoveryResponder:
    """Answers UDP broadcast probes with the server's reachable base URLs."""

    def __init__(self, api_port: int = 8100, port: int = DISCOVERY_PORT) -> None:
        self._api_port = api_port
        self._port = port
        self._socket: socket.socket | None = None
        self._thread: threading.Thread | None = None
        self._stopping = threading.Event()

    def start(self) -> None:
        if self._thread is not None:
            return
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind(("0.0.0.0", self._port))
        except OSError:
            sock.close()
            return
        sock.settimeout(0.5)
        self._socket = sock
        self._thread = threading.Thread(
            target=self._serve,
            name="online-discovery-responder",
            daemon=True,
        )
        self._thread.start()

    def stop(self) -> None:
        self._stopping.set()
        sock = self._socket
        if sock is not None:
            try:
                sock.close()
            except OSError:
                pass
        thread = self._thread
        if thread is not None:
            thread.join(timeout=2.0)
        self._socket = None
        self._thread = None

    def _serve(self) -> None:
        sock = self._socket
        if sock is None:
            return
        while not self._stopping.is_set():
            try:
                payload, peer = sock.recvfrom(2048)
            except socket.timeout:
                continue
            except OSError:
                break
            try:
                message = payload.decode("utf-8", "replace")
            except Exception:
                continue
            if DISCOVERY_MAGIC not in message:
                continue
            reply = json.dumps(
                {
                    "service": "reverse-tutor-online",
                    "magic": DISCOVERY_MAGIC,
                    "baseUrls": [
                        f"http://{address}:{self._api_port}"
                        for address in _candidate_ipv4()
                    ],
                }
            ).encode("utf-8")
            try:
                sock.sendto(reply, peer)
            except OSError:
                continue


def _candidate_ipv4() -> list[str]:
    candidates: list[str] = []

    def _add(value: str) -> None:
        if not value or value.startswith("127."):
            return
        if value.startswith("169.254."):
            return
        if value not in candidates:
            candidates.append(value)

    try:
        import psutil

        for addresses in psutil.net_if_addrs().values():
            for address in addresses:
                if address.family == socket.AF_INET:
                    _add(address.address)
    except Exception:
        pass
    try:
        hostname = socket.gethostname()
        for info in socket.getaddrinfo(hostname, None, socket.AF_INET):
            _add(info[4][0])
    except OSError:
        pass
    try:
        probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            probe.connect(("192.168.255.255", 9))
            _add(probe.getsockname()[0])
        finally:
            probe.close()
    except OSError:
        pass
    return candidates
