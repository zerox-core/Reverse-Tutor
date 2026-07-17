from __future__ import annotations

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Response, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .auth_memory import InMemoryAuthStore, InMemoryIdempotencyStore
from .auth_models import (
    AnonymousBootstrapRequest,
    AuthContext,
    AuthMeResponse,
    AuthSessionPage,
    AuthTokenResponse,
    RefreshRequest,
)
from .auth_service import AuthService
from .auth_tokens import AuthSettings, AuthTokenCodec
from .errors import OnlineApiError
from .ports import AuthStore, IdempotencyStore


router = APIRouter(prefix="/api/v1/auth", tags=["Auth"])


def create_memory_auth_service() -> AuthService:
    settings = AuthSettings.ephemeral_memory()
    return AuthService(
        auth_store=InMemoryAuthStore(),
        idempotency_store=InMemoryIdempotencyStore(),
        token_codec=AuthTokenCodec(settings),
        settings=settings,
    )


def create_production_auth_service(
    auth_store: AuthStore,
    idempotency_store: IdempotencyStore,
) -> AuthService:
    settings = AuthSettings.from_env()
    return AuthService(
        auth_store=auth_store,
        idempotency_store=idempotency_store,
        token_codec=AuthTokenCodec(settings),
        settings=settings,
    )


_auth_service = create_memory_auth_service()


def set_auth_service(service: AuthService) -> None:
    global _auth_service
    previous = _auth_service
    _auth_service = service
    if previous is not service:
        previous.close()


def reset_auth_service() -> AuthService:
    set_auth_service(create_memory_auth_service())
    return _auth_service


def get_auth_service() -> AuthService:
    return _auth_service


AuthServiceDep = Annotated[AuthService, Depends(get_auth_service)]
bearer_scheme = HTTPBearer(
    auto_error=False,
    bearerFormat="JWT",
    scheme_name="bearerAuth",
)


def require_auth(
    service: AuthServiceDep,
    credentials: Annotated[
        HTTPAuthorizationCredentials | None,
        Depends(bearer_scheme),
    ] = None,
) -> AuthContext:
    if (
        credentials is None
        or credentials.scheme.lower() != "bearer"
        or not credentials.credentials.strip()
    ):
        raise OnlineApiError(
            401,
            "unauthorized",
            "Authentication is required",
            user_action="bootstrap_anonymous",
        )
    return service.authenticate(credentials.credentials.strip())


AuthContextDep = Annotated[AuthContext, Depends(require_auth)]


@router.post(
    "/anonymous",
    response_model=AuthTokenResponse,
    status_code=status.HTTP_201_CREATED,
)
def bootstrap_anonymous(
    request: AnonymousBootstrapRequest,
    service: AuthServiceDep,
) -> AuthTokenResponse:
    return service.bootstrap(request)


@router.post("/refresh", response_model=AuthTokenResponse)
def refresh_auth(
    request: RefreshRequest,
    service: AuthServiceDep,
) -> AuthTokenResponse:
    return service.refresh(request)


@router.get("/me", response_model=AuthMeResponse)
def auth_me(context: AuthContextDep, service: AuthServiceDep) -> AuthMeResponse:
    return service.me(context)


@router.get("/sessions", response_model=AuthSessionPage)
def auth_sessions(
    context: AuthContextDep,
    service: AuthServiceDep,
) -> AuthSessionPage:
    return service.list_sessions(context)


@router.delete("/sessions/{sessionId}", status_code=status.HTTP_204_NO_CONTENT)
def revoke_auth_session(
    sessionId: UUID,
    context: AuthContextDep,
    service: AuthServiceDep,
) -> Response:
    service.revoke_session(context, sessionId)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
