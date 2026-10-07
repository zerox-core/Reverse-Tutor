from __future__ import annotations

from contextvars import ContextVar
from uuid import uuid4


MAX_REQUEST_ID_LENGTH = 128
request_id_var: ContextVar[str] = ContextVar("online_request_id", default="")


def request_id_from_header(value: str | None) -> str:
    if value and len(value) <= MAX_REQUEST_ID_LENGTH:
        return value
    return f"req_{uuid4()}"


def current_request_id() -> str:
    return request_id_var.get() or f"req_{uuid4()}"
