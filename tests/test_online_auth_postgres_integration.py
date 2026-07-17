from __future__ import annotations

import os
from pathlib import Path
from uuid import UUID, uuid4

import httpx
import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import func, select

from adapters.online.auth_routes import reset_auth_service
from online_db.models import (
    AccountDevice,
    AnonymousAccount,
    AuthSession,
    IdempotencyRecord,
    RefreshToken,
)
from online_db.session import build_online_session_factory


POSTGRES_URL = (
    os.getenv("TEST_ONLINE_DATABASE_URL")
    or os.getenv("TEST_POSTGRES_URL")
    or ""
).strip()


@pytest.mark.skipif(not POSTGRES_URL, reason="TEST_ONLINE_DATABASE_URL is required")
async def test_fastapi_auth_uses_configured_postgres_store(monkeypatch):
    from server import app, configure_online_auth_from_env

    alembic_config = Config(str(Path("alembic.ini").resolve()))
    alembic_config.set_main_option("sqlalchemy.url", POSTGRES_URL)
    command.upgrade(alembic_config, "head")

    monkeypatch.setenv("ONLINE_DATABASE_URL", POSTGRES_URL)
    monkeypatch.setenv(
        "ONLINE_AUTH_SIGNING_KEY",
        "integration-signing-key-with-at-least-32-bytes",
    )
    monkeypatch.setenv(
        "ONLINE_AUTH_REFRESH_PEPPER",
        "integration-refresh-pepper-with-at-least-32-bytes",
    )
    monkeypatch.setenv(
        "ONLINE_AUTH_IDEMPOTENCY_SEALING_KEY",
        "integration-idempotency-sealing-key",
    )
    configure_online_auth_from_env()

    device_id = f"device-{uuid4()}"
    idempotency_key = f"bootstrap-{uuid4()}"
    try:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(
            transport=transport,
            base_url="http://testserver",
        ) as client:
            response = await client.post(
                "/api/v1/auth/anonymous",
                json={
                    "deviceId": device_id,
                    "idempotencyKey": idempotency_key,
                },
            )

        assert response.status_code == 201
        payload = response.json()
        account_id = UUID(payload["accountId"])
        session_id = UUID(payload["sessionId"])

        session_factory = build_online_session_factory(POSTGRES_URL)
        try:
            with session_factory() as database:
                assert database.scalar(
                    select(func.count())
                    .select_from(AnonymousAccount)
                    .where(AnonymousAccount.id == account_id)
                ) == 1
                assert database.scalar(
                    select(func.count())
                    .select_from(AccountDevice)
                    .where(AccountDevice.client_device_id == device_id)
                ) == 1
                assert database.scalar(
                    select(func.count())
                    .select_from(AuthSession)
                    .where(AuthSession.id == session_id)
                ) == 1
                assert database.scalar(
                    select(func.count())
                    .select_from(RefreshToken)
                    .where(RefreshToken.session_id == session_id)
                ) == 1
                assert database.scalar(
                    select(func.count())
                    .select_from(IdempotencyRecord)
                    .where(
                        IdempotencyRecord.scope_key == f"auth.bootstrap:{device_id}",
                        IdempotencyRecord.idempotency_key == idempotency_key,
                        IdempotencyRecord.response_status_code == 201,
                        IdempotencyRecord.response_ciphertext.is_not(None),
                    )
                ) == 1
        finally:
            session_factory.kw["bind"].dispose()
    finally:
        reset_auth_service()
