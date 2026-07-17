from __future__ import annotations

from typing import Any

from fastapi.responses import JSONResponse

from .request_context import current_request_id


class OnlineApiError(Exception):
    def __init__(
        self,
        status_code: int,
        code: str,
        message: str,
        *,
        retryable: bool = False,
        user_action: str = "none",
        details: dict[str, object] | None = None,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message
        self.retryable = retryable
        self.user_action = user_action
        self.details = details or {}


def error_response(
    status_code: int,
    code: str,
    message: str,
    *,
    retryable: bool = False,
    user_action: str = "none",
    details: dict[str, Any] | None = None,
) -> JSONResponse:
    request_id = current_request_id()
    return JSONResponse(
        status_code=status_code,
        content={
            "error": {
                "code": code,
                "message": message,
                "retryable": retryable,
                "userAction": user_action,
                "requestId": request_id,
                "details": details or {},
            }
        },
        headers={"X-Request-Id": request_id},
    )
