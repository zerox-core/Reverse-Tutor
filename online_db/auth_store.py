from __future__ import annotations

from datetime import datetime, timezone
from uuid import UUID, uuid4

from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from adapters.online.ports import (
    AuthSessionRecord,
    BootstrapCommand,
    BootstrapResult,
    DeviceAlreadyRegistered,
    RefreshTokenInvalid,
    RefreshTokenReplay,
    RotateRefreshCommand,
    RotateRefreshResult,
)
from adapters.online.request_context import current_request_id
from online_db.models import (
    AccountDevice,
    AnonymousAccount,
    AuthAuditEvent,
    AuthSession,
    RefreshToken,
)


class SqlAlchemyAuthStore:
    def __init__(self, session_factory: sessionmaker[Session]) -> None:
        self._session_factory = session_factory

    def close(self) -> None:
        bind = self._session_factory.kw.get("bind")
        if bind is not None:
            bind.dispose()

    def bootstrap(self, command: BootstrapCommand) -> BootstrapResult:
        try:
            return self._bootstrap_once(command)
        except IntegrityError as exc:
            if self._device_exists(command.device_id):
                raise DeviceAlreadyRegistered(
                    "Device is already registered"
                ) from exc
            raise

    def _bootstrap_once(self, command: BootstrapCommand) -> BootstrapResult:
        with self._session_factory() as database, database.begin():
            device = database.scalar(
                select(AccountDevice)
                .where(AccountDevice.client_device_id == command.device_id)
                .with_for_update()
            )
            if device is None:
                account = AnonymousAccount(
                    id=uuid4(),
                    status="active",
                    created_at=command.now,
                    last_seen_at=command.now,
                )
                device = AccountDevice(
                    id=uuid4(),
                    account_id=account.id,
                    client_device_id=command.device_id,
                    status="active",
                    created_at=command.now,
                    last_seen_at=command.now,
                )
                database.add(account)
                database.flush()
                database.add(device)
                database.flush()
            else:
                raise DeviceAlreadyRegistered("Device is already registered")

            auth_session = database.scalar(
                select(AuthSession)
                .where(
                    AuthSession.device_id == device.id,
                    AuthSession.status == "active",
                    AuthSession.expires_at > command.now,
                )
                .order_by(AuthSession.created_at.desc(), AuthSession.id.desc())
                .limit(1)
                .with_for_update()
            )
            if auth_session is None:
                auth_session = AuthSession(
                    id=uuid4(),
                    account_id=account.id,
                    device_id=device.id,
                    status="active",
                    created_at=command.now,
                    expires_at=command.refresh_expires_at,
                    last_seen_at=command.now,
                )
                database.add(auth_session)
                database.flush()
                family_id = uuid4()
                rotation = 0
            else:
                auth_session.expires_at = command.refresh_expires_at
                auth_session.last_seen_at = command.now
                latest_token = database.scalar(
                    select(RefreshToken)
                    .where(RefreshToken.session_id == auth_session.id)
                    .order_by(RefreshToken.rotation.desc())
                    .limit(1)
                    .with_for_update()
                )
                family_id = latest_token.family_id if latest_token else uuid4()
                rotation = latest_token.rotation + 1 if latest_token else 0

            database.add(
                RefreshToken(
                    id=uuid4(),
                    session_id=auth_session.id,
                    family_id=family_id,
                    token_hash=command.initial_refresh_token_hash,
                    rotation=rotation,
                    expires_at=command.refresh_expires_at,
                    created_at=command.now,
                )
            )
            self._add_audit(
                database,
                event_type="auth.bootstrap",
                account_id=account.id,
                device_id=device.id,
                session_id=auth_session.id,
                details={"client_device_id": command.device_id},
                now=command.now,
            )
            result = BootstrapResult(
                session=self._record(auth_session, device),
                family_id=family_id,
            )
        return result

    def rotate_refresh(self, command: RotateRefreshCommand) -> RotateRefreshResult:
        replay_detected = False
        result: RotateRefreshResult | None = None
        with self._session_factory() as database, database.begin():
            token = database.scalar(
                select(RefreshToken)
                .where(RefreshToken.token_hash == command.presented_token_hash)
                .with_for_update()
            )
            if token is None:
                raise RefreshTokenInvalid("Refresh token is invalid")

            if token.used_at is not None:
                self._revoke_family(database, token.family_id, command.now)
                self._add_audit(
                    database,
                    event_type="refresh.replay",
                    account_id=None,
                    device_id=None,
                    session_id=token.session_id,
                    details={"family_id": str(token.family_id)},
                    now=command.now,
                    outcome="rejected",
                )
                replay_detected = True
            elif (
                token.revoked_at is not None
                or _as_utc(token.expires_at) <= _as_utc(command.now)
            ):
                raise RefreshTokenInvalid("Refresh token is invalid or expired")
            else:
                row = database.execute(
                    select(AuthSession, AccountDevice)
                    .join(AccountDevice, AuthSession.device_id == AccountDevice.id)
                    .where(AuthSession.id == token.session_id)
                    .with_for_update()
                ).one_or_none()
                if row is None:
                    raise RefreshTokenInvalid("Refresh session is inactive")
                auth_session, device = row
                if (
                    auth_session.status != "active"
                    or _as_utc(auth_session.expires_at) <= _as_utc(command.now)
                    or device.status != "active"
                ):
                    raise RefreshTokenInvalid("Refresh session is inactive")

                replacement = RefreshToken(
                    id=uuid4(),
                    session_id=auth_session.id,
                    family_id=token.family_id,
                    token_hash=command.replacement_token_hash,
                    rotation=token.rotation + 1,
                    expires_at=command.refresh_expires_at,
                    created_at=command.now,
                )
                token.used_at = command.now
                auth_session.expires_at = command.refresh_expires_at
                auth_session.last_seen_at = command.now
                database.add(replacement)
                database.flush([replacement])
                token.replaced_by_id = replacement.id
                self._add_audit(
                    database,
                    event_type="refresh.rotate",
                    account_id=auth_session.account_id,
                    device_id=device.id,
                    session_id=auth_session.id,
                    details={"rotation": replacement.rotation},
                    now=command.now,
                )
                result = RotateRefreshResult(
                    session=self._record(auth_session, device),
                    family_id=token.family_id,
                    rotation=replacement.rotation,
                )

        if replay_detected:
            raise RefreshTokenReplay("Refresh token replay detected")
        if result is None:
            raise AssertionError("Refresh rotation produced no result")
        return result

    def active_session(
        self, session_id: UUID, now: datetime
    ) -> AuthSessionRecord | None:
        with self._session_factory() as database, database.begin():
            row = database.execute(
                select(AuthSession, AccountDevice)
                .join(AccountDevice, AuthSession.device_id == AccountDevice.id)
                .join(AnonymousAccount, AuthSession.account_id == AnonymousAccount.id)
                .where(
                    AuthSession.id == session_id,
                    AuthSession.status == "active",
                    AccountDevice.status == "active",
                    AnonymousAccount.status == "active",
                )
                .with_for_update()
            ).one_or_none()
            if row is None:
                return None
            auth_session, device = row
            if _as_utc(auth_session.expires_at) <= _as_utc(now):
                auth_session.status = "expired"
                return None
            return self._record(auth_session, device)

    def list_sessions(self, account_id: UUID) -> list[AuthSessionRecord]:
        with self._session_factory() as database:
            rows = database.execute(
                select(AuthSession, AccountDevice)
                .join(AccountDevice, AuthSession.device_id == AccountDevice.id)
                .where(AuthSession.account_id == account_id)
                .order_by(AuthSession.created_at.desc(), AuthSession.id.desc())
            ).all()
            return [self._record(auth_session, device) for auth_session, device in rows]

    def revoke_session(
        self, account_id: UUID, session_id: UUID, now: datetime
    ) -> bool:
        with self._session_factory() as database, database.begin():
            row = database.execute(
                select(AuthSession, AccountDevice)
                .join(AccountDevice, AuthSession.device_id == AccountDevice.id)
                .where(
                    AuthSession.id == session_id,
                    AuthSession.account_id == account_id,
                )
                .with_for_update()
            ).one_or_none()
            if row is None:
                return False
            auth_session, device = row
            if auth_session.status == "revoked":
                return True

            auth_session.status = "revoked"
            auth_session.revoked_at = now
            database.execute(
                update(RefreshToken)
                .where(
                    RefreshToken.session_id == session_id,
                    RefreshToken.revoked_at.is_(None),
                )
                .values(revoked_at=now)
            )
            self._add_audit(
                database,
                event_type="session.revoke",
                account_id=account_id,
                device_id=device.id,
                session_id=session_id,
                details={},
                now=now,
            )
            return True

    def _device_exists(self, client_device_id: str) -> bool:
        with self._session_factory() as database:
            return database.scalar(
                select(AccountDevice.id).where(
                    AccountDevice.client_device_id == client_device_id
                )
            ) is not None

    @staticmethod
    def _record(
        auth_session: AuthSession, device: AccountDevice
    ) -> AuthSessionRecord:
        return AuthSessionRecord(
            account_id=auth_session.account_id,
            device_id=device.client_device_id,
            session_id=auth_session.id,
            status=auth_session.status,
            created_at=_as_utc(auth_session.created_at),
            expires_at=_as_utc(auth_session.expires_at),
        )

    @staticmethod
    def _add_audit(
        database: Session,
        *,
        event_type: str,
        account_id: UUID | None,
        device_id: UUID | None,
        session_id: UUID | None,
        details: dict[str, object],
        now: datetime,
        outcome: str = "success",
    ) -> None:
        database.add(
            AuthAuditEvent(
                id=uuid4(),
                account_id=account_id,
                device_id=device_id,
                session_id=session_id,
                event_type=event_type,
                outcome=outcome,
                request_id=current_request_id(),
                safe_details=details,
                created_at=now,
            )
        )

    @staticmethod
    def _revoke_family(database: Session, family_id: UUID, now: datetime) -> None:
        session_ids = select(RefreshToken.session_id).where(
            RefreshToken.family_id == family_id
        )
        database.execute(
            update(RefreshToken)
            .where(
                RefreshToken.family_id == family_id,
                RefreshToken.revoked_at.is_(None),
            )
            .values(revoked_at=now)
        )
        database.execute(
            update(AuthSession)
            .where(
                AuthSession.id.in_(session_ids),
                AuthSession.status == "active",
            )
            .values(status="revoked", revoked_at=now)
        )


def _as_utc(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)
