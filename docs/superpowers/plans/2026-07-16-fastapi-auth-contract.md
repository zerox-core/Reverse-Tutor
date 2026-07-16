# FastAPI Authentication Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add canonical anonymous-device authentication, request tracing, error envelopes, and Bearer-derived identity to `/api/v1` while keeping online storage behind an injectable port.

**Architecture:** FastAPI owns HTTP DTOs, token encoding, error mapping and authorization. An `AuthStore` protocol owns atomic persistence operations; Slice 0 uses an in-memory implementation for protocol tests, while the PostgreSQL plan supplies the production implementation. Android `core:remote` receives matching Auth API methods and stops sending `userId` as an authorization field.

**Tech Stack:** FastAPI, Pydantic 2, PyJWT, Python `secrets`/HMAC-SHA256, pytest/httpx, Kotlin, kotlinx JSON, JUnit 4.

---

### Task 1: Freeze Canonical Auth And Error Schemas

**Files:**
- Modify: `docs/contracts/openapi-online-v1.yaml`
- Modify: `docs/contracts/mock-online-v1.json`
- Create: `tests/test_online_openapi_contract.py`

- [ ] **Step 1: Write a failing OpenAPI contract test**

```python
from pathlib import Path

import yaml


def test_slice_zero_openapi_contains_auth_and_error_contracts():
    document = yaml.safe_load(
        Path("docs/contracts/openapi-online-v1.yaml").read_text(encoding="utf-8")
    )
    paths = document["paths"]
    assert set(
        [
            "/auth/anonymous",
            "/auth/refresh",
            "/auth/me",
            "/auth/sessions",
            "/auth/sessions/{sessionId}",
        ]
    ).issubset(paths)
    error = document["components"]["schemas"]["ApiError"]
    assert error["required"] == [
        "code", "message", "retryable", "userAction", "requestId"
    ]


def test_write_identity_does_not_trust_body_user_id():
    document = yaml.safe_load(
        Path("docs/contracts/openapi-online-v1.yaml").read_text(encoding="utf-8")
    )
    properties = document["components"]["schemas"]["OnlineWriteIdentity"]["properties"]
    assert "userId" not in properties
```

- [ ] **Step 2: Run the test and verify RED**

```powershell
py -m pytest tests/test_online_openapi_contract.py -v
```

Expected: auth paths are missing and `OnlineWriteIdentity` still exposes `userId`.

- [ ] **Step 3: Add the canonical schemas**

Add these request/response shapes:

```yaml
AnonymousBootstrapRequest:
  type: object
  additionalProperties: false
  required: [deviceId, idempotencyKey]
  properties:
    deviceId: {type: string, minLength: 16, maxLength: 128}
    idempotencyKey: {type: string, minLength: 1, maxLength: 128}
    appVersionCode: {type: [integer, 'null'], format: int64, minimum: 1}
RefreshRequest:
  type: object
  additionalProperties: false
  required: [refreshToken, idempotencyKey]
  properties:
    refreshToken: {type: string, minLength: 32, maxLength: 4096}
    idempotencyKey: {type: string, minLength: 1, maxLength: 128}
AuthTokenResponse:
  type: object
  additionalProperties: false
  required:
    - accountId
    - deviceId
    - sessionId
    - tokenType
    - accessToken
    - accessTokenExpiresAtEpochMillis
    - refreshToken
    - refreshTokenExpiresAtEpochMillis
  properties:
    accountId: {type: string, format: uuid}
    deviceId: {type: string}
    sessionId: {type: string, format: uuid}
    tokenType: {type: string, const: Bearer}
    accessToken: {type: string}
    accessTokenExpiresAtEpochMillis: {type: integer, format: int64}
    refreshToken: {type: string}
    refreshTokenExpiresAtEpochMillis: {type: integer, format: int64}
```

Add `AuthMeResponse` and `AuthSessionPage`, then add the five auth paths. Public bootstrap and refresh have `security: []`; me/session endpoints require `bearerAuth`.

Remove `userId` from write, sync and weekly request schemas. Add `reauthenticate` and `bootstrap_anonymous` to `ApiError.userAction`.

- [ ] **Step 4: Update the mock fixture**

Use deterministic non-secret fixture values such as `fixture-access-token` and `fixture-refresh-token`; label them as fixture-only and never load them in production configuration.

- [ ] **Step 5: Run tests and verify GREEN**

```powershell
py -m pytest tests/test_online_openapi_contract.py -v
```

- [ ] **Step 6: Commit the canonical contract**

```powershell
git add docs/contracts/openapi-online-v1.yaml docs/contracts/mock-online-v1.json tests/test_online_openapi_contract.py
git commit -m "docs: freeze anonymous auth contract"
```

### Task 2: Add Request IDs And Canonical Errors

**Files:**
- Create: `adapters/online/request_context.py`
- Create: `adapters/online/errors.py`
- Modify: `server.py`
- Create: `tests/test_online_error_contract.py`

- [ ] **Step 1: Write failing error envelope tests**

```python
async def test_online_errors_are_canonical_and_echo_request_id(client):
    response = await client.get(
        "/api/v1/activities/missing",
        headers={"X-Request-Id": "req-contract-1"},
    )
    assert response.status_code == 404
    assert response.headers["X-Request-Id"] == "req-contract-1"
    assert response.json() == {
        "error": {
            "code": "activity_not_found",
            "message": "Activity not found",
            "retryable": False,
            "userAction": "none",
            "requestId": "req-contract-1",
            "details": {},
        }
    }


async def test_validation_error_does_not_return_fastapi_detail(client):
    response = await client.post("/api/v1/auth/anonymous", json={})
    assert response.status_code == 422
    assert set(response.json()) == {"error"}
    assert response.json()["error"]["code"] == "invalid_request"
```

- [ ] **Step 2: Run and verify RED**

```powershell
py -m pytest tests/test_online_error_contract.py -v
```

Expected: responses contain FastAPI `detail` and no guaranteed request-id header.

- [ ] **Step 3: Implement request context and domain exception**

```python
request_id_var: ContextVar[str] = ContextVar("online_request_id", default="")


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
```

Add middleware that validates an incoming `X-Request-Id` length up to 128, otherwise generates `req_<uuid>`, stores it in the context variable and returns it on every response. Add handlers for `OnlineApiError`, `RequestValidationError`, and unexpected exceptions. Unexpected errors return `server_error` without stack or raw body.

- [ ] **Step 4: Replace `/api/v1` route `HTTPException` calls**

Use stable codes: `activity_not_found`, `unauthorized`, `invalid_request`, `revision_conflict`, and `server_error`. Do not modify legacy `/api/*` error behavior in this task.

- [ ] **Step 5: Run tests and verify GREEN**

```powershell
py -m pytest tests/test_online_error_contract.py tests/test_online_hybrid_api.py -v
```

- [ ] **Step 6: Commit error infrastructure**

```powershell
git add adapters/online/request_context.py adapters/online/errors.py adapters/online/routes.py server.py tests/test_online_error_contract.py
git commit -m "feat: add canonical online errors"
```

### Task 3: Define The AuthStore Port And In-Memory Test Store

**Files:**
- Create: `adapters/online/ports.py`
- Create: `adapters/online/auth_memory.py`
- Create: `tests/test_online_auth_store_contract.py`

- [ ] **Step 1: Write failing port contract tests**

```python
def test_bootstrap_is_idempotent_for_same_device_and_fingerprint(clock):
    store = InMemoryIdempotencyStore()
    first = store.claim("auth.bootstrap:device-0123456789", "bootstrap-1", "sha256:one", clock.now)
    store.complete(first.claim_id, sealed_fixture_response(), clock.now)
    second = store.claim("auth.bootstrap:device-0123456789", "bootstrap-1", "sha256:one", clock.now)
    assert second.replayed_response == sealed_fixture_response()


def test_reusing_idempotency_key_with_other_fingerprint_is_rejected(clock):
    store = InMemoryIdempotencyStore()
    store.claim("auth.bootstrap:device-0123456789", "bootstrap-1", "sha256:one", clock.now)
    with pytest.raises(IdempotencyKeyReused):
        store.claim("auth.bootstrap:device-0123456789", "bootstrap-1", "sha256:two", clock.now)
```

- [ ] **Step 2: Run and verify RED**

```powershell
py -m pytest tests/test_online_auth_store_contract.py -v
```

- [ ] **Step 3: Define immutable port records**

```python
@dataclass(frozen=True)
class AuthSessionRecord:
    account_id: UUID
    device_id: str
    session_id: UUID
    status: str
    created_at: datetime
    expires_at: datetime


@dataclass(frozen=True)
class BootstrapCommand:
    device_id: str
    initial_refresh_token_hash: str
    now: datetime
    refresh_expires_at: datetime


@dataclass(frozen=True)
class BootstrapResult:
    session: AuthSessionRecord
    family_id: UUID


@dataclass(frozen=True)
class RotateRefreshCommand:
    presented_token_hash: str
    replacement_token_hash: str
    now: datetime
    refresh_expires_at: datetime


@dataclass(frozen=True)
class RotateRefreshResult:
    session: AuthSessionRecord
    family_id: UUID
    rotation: int


@dataclass(frozen=True)
class SealedResponse:
    ciphertext: bytes
    nonce: bytes
    key_id: str
    expires_at: datetime


@dataclass(frozen=True)
class IdempotencyClaim:
    claim_id: UUID
    replayed_response: SealedResponse | None


class AuthStore(Protocol):
    def bootstrap(self, command: BootstrapCommand) -> BootstrapResult: ...
    def rotate_refresh(self, command: RotateRefreshCommand) -> RotateRefreshResult: ...
    def active_session(self, session_id: UUID, now: datetime) -> AuthSessionRecord | None: ...
    def list_sessions(self, account_id: UUID) -> list[AuthSessionRecord]: ...
    def revoke_session(self, account_id: UUID, session_id: UUID, now: datetime) -> bool: ...


class IdempotencyStore(Protocol):
    def claim(
        self,
        scope: str,
        key: str,
        request_fingerprint: str,
        now: datetime,
    ) -> IdempotencyClaim: ...

    def complete(
        self,
        claim_id: UUID,
        sealed_response: SealedResponse,
        now: datetime,
    ) -> None: ...
```

The store receives hashes only. It never receives access-token or refresh-token plaintext.

- [ ] **Step 4: Implement the locked in-memory store**

Use `RLock`, idempotency scope `(operation, device_id, idempotency_key)`, stored request fingerprints and refresh token families. Idempotency responses are represented by encrypted `SealedResponse` values. Reusing a consumed refresh token must revoke its family and raise `RefreshTokenReplay`.

- [ ] **Step 5: Run and verify GREEN**

Use the Task 3 Step 2 command.

- [ ] **Step 6: Commit the port**

```powershell
git add adapters/online/ports.py adapters/online/auth_memory.py tests/test_online_auth_store_contract.py
git commit -m "feat: define online auth store port"
```

### Task 4: Implement Token Encoding And Anonymous Auth Service

**Files:**
- Create: `adapters/online/auth_tokens.py`
- Create: `adapters/online/auth_service.py`
- Create: `adapters/online/auth_models.py`
- Create: `tests/test_online_auth_service.py`

- [ ] **Step 1: Write failing token and rotation tests**

```python
def test_access_token_contains_only_required_identity_claims(auth_service, clock):
    response = auth_service.bootstrap(
        AnonymousBootstrapRequest(
            deviceId="device-0123456789",
            idempotencyKey="bootstrap-1",
        )
    )
    claims = auth_service.token_codec.decode_access(response.access_token, now=clock.now)
    assert claims.subject == response.account_id
    assert claims.session_id == response.session_id
    assert claims.device_id == response.device_id
    assert "refreshToken" not in claims.raw


def test_refresh_rotates_and_replay_revokes_family(auth_service):
    issued = bootstrap(auth_service)
    rotated = auth_service.refresh(
        RefreshRequest(refreshToken=issued.refresh_token, idempotencyKey="refresh-1")
    )
    assert rotated.refresh_token != issued.refresh_token
    with pytest.raises(RefreshTokenReplay):
        auth_service.refresh(
            RefreshRequest(refreshToken=issued.refresh_token, idempotencyKey="refresh-2")
        )
```

- [ ] **Step 2: Run and verify RED**

```powershell
py -m pytest tests/test_online_auth_service.py -v
```

- [ ] **Step 3: Implement token rules**

Access JWT claims:

```python
payload = {
    "iss": settings.issuer,
    "aud": settings.audience,
    "sub": str(session.account_id),
    "sid": str(session.session_id),
    "did": session.device_id,
    "jti": str(uuid4()),
    "iat": int(now.timestamp()),
    "exp": int(access_expires_at.timestamp()),
}
```

Generate refresh tokens with `secrets.token_urlsafe(48)`. Hash them with `hmac.new(pepper, token.encode(), hashlib.sha256).hexdigest()`. Secrets must come from injected settings; production construction fails when signing key or pepper is absent.

- [ ] **Step 4: Implement service methods**

`bootstrap`, `refresh`, `authenticate`, `me`, `list_sessions` and `revoke_session` map store errors to stable `OnlineApiError` codes. Access lifetime defaults to 15 minutes; refresh lifetime defaults to 30 days and remains configurable.

- [ ] **Step 5: Run tests and verify GREEN**

Use the Task 4 Step 2 command.

- [ ] **Step 6: Commit auth service**

```powershell
git add adapters/online/auth_tokens.py adapters/online/auth_service.py adapters/online/auth_models.py tests/test_online_auth_service.py
git commit -m "feat: add anonymous auth service"
```

### Task 5: Expose Auth Routes And Protect Online Writes

**Files:**
- Create: `adapters/online/auth_routes.py`
- Modify: `adapters/online/__init__.py`
- Modify: `adapters/online/models.py`
- Modify: `adapters/online/routes.py`
- Modify: `adapters/online/service.py`
- Create: `tests/test_online_auth_api.py`
- Modify: `tests/test_online_hybrid_api.py`

- [ ] **Step 1: Write failing HTTP auth tests**

```python
async def test_bootstrap_me_list_and_revoke(client):
    issued = await bootstrap_client(client)
    headers = {"Authorization": f"Bearer {issued['accessToken']}"}
    me = await client.get("/api/v1/auth/me", headers=headers)
    sessions = await client.get("/api/v1/auth/sessions", headers=headers)
    revoked = await client.delete(
        f"/api/v1/auth/sessions/{issued['sessionId']}", headers=headers
    )
    assert me.status_code == 200
    assert sessions.json()["items"][0]["sessionId"] == issued["sessionId"]
    assert revoked.status_code == 204


async def test_protected_write_uses_bearer_subject_not_body_user(client):
    issued = await bootstrap_client(client)
    response = await client.post(
        "/api/v1/activities/focus-week/join",
        headers={"Authorization": f"Bearer {issued['accessToken']}"},
        json={
            "deviceId": issued["deviceId"],
            "revision": 1,
            "idempotencyKey": "join-1",
        },
    )
    assert response.status_code == 200
    assert response.json()["userId"] == issued["accountId"]
```

- [ ] **Step 2: Run and verify RED**

```powershell
py -m pytest tests/test_online_auth_api.py tests/test_online_hybrid_api.py -v
```

- [ ] **Step 3: Add auth routes**

```python
@router.post("/anonymous", response_model=AuthTokenResponse, status_code=201)
def bootstrap_anonymous(request: AnonymousBootstrapRequest, service: AuthServiceDep):
    return service.bootstrap(request)


@router.post("/refresh", response_model=AuthTokenResponse)
def refresh(request: RefreshRequest, service: AuthServiceDep):
    return service.refresh(request)


@router.get("/me", response_model=AuthMeResponse)
def me(context: AuthContextDep, service: AuthServiceDep):
    return service.me(context)
```

Define the dependencies in the same module:

```python
AuthServiceDep = Annotated[AuthService, Depends(get_auth_service)]
AuthContextDep = Annotated[AuthContext, Depends(require_auth)]
```

Add list and revoke endpoints. Return 204 for repeated revoke of the caller's session; return 404 for sessions outside the account to prevent enumeration.

- [ ] **Step 4: Remove body `userId` from protected request models**

`OnlineWriteIdentity`, `SyncPushRequest`, `SyncPullRequest`, and `WeeklyInsightRequest` retain `deviceId` but remove `userId`. Update service methods to accept `account_id` from `AuthContext` and use it for all idempotency and ownership keys.

Require Bearer auth on join, progress, leave, sync push/pull and weekly insight. Keep activity list/detail/leaderboard, content and releases public.

- [ ] **Step 5: Run tests and verify GREEN**

Use the Task 5 Step 2 command.

- [ ] **Step 6: Commit HTTP auth**

```powershell
git add adapters/online tests/test_online_auth_api.py tests/test_online_hybrid_api.py
git commit -m "feat: protect online writes with bearer auth"
```

### Task 6: Add Android AuthApi And Identity-Free Requests

**Files:**
- Create: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/OnlineAuthModels.kt`
- Create: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/AuthApi.kt`
- Modify: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/OnlineCapabilities.kt`
- Modify: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/OnlineApi.kt`
- Modify: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/OnlineHttpTransport.kt`
- Modify: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/UrlConnectionOnlineHttpTransport.kt`
- Modify: `mobile-native/core/remote/src/main/java/com/reversetutor/core/remote/HttpOnlineApi.kt`
- Modify: `mobile-native/core/remote/src/test/java/com/reversetutor/core/remote/HttpOnlineApiTest.kt`
- Modify: `mobile-native/core/remote/src/test/java/com/reversetutor/core/remote/OnlineCapabilitiesTest.kt`

- [ ] **Step 1: Write failing Android request tests**

```kotlin
@Test
fun bootstrapIsPublicAndDecodesTokens() = runTest {
    val transport = RecordingTransport(
        response = authTokenResponseJson()
    )
    val result = HttpOnlineApi("https://api.test", transport).bootstrapAnonymous(
        AnonymousBootstrapRequest("device-0123456789", "bootstrap-1", 1)
    )
    assertFalse(transport.lastRequest.headers.containsKey("Authorization"))
    assertEquals("account-1", (result as OnlineResult.Success).value.accountId)
}

@Test
fun protectedWriteDoesNotSerializeUserId() = runTest {
    val api = authenticatedRecordingApi(activityParticipationJson())
    api.joinActivity(
        "focus-week",
        OnlineWriteIdentity("device-0123456789", 1, "join-1")
    )
    assertFalse(api.lastBody.has("userId"))
}

@Test
fun requestAndErrorMetadataArePreserved() = runTest {
    val api = recordingApi(
        status = 401,
        headers = mapOf("X-Request-Id" to "req-auth-1"),
        body = canonicalUnauthorizedJson("req-auth-1")
    )
    val result = api.authMe()
    assertEquals(
        OnlineResult.Failure(
            code = "unauthorized",
            retryable = false,
            userAction = "bootstrap_anonymous",
            requestId = "req-auth-1"
        ),
        result
    )
}
```

- [ ] **Step 2: Run and verify RED**

```powershell
mobile-native\gradlew.bat :core:remote:testDebugUnitTest --tests "*HttpOnlineApiTest" --tests "*OnlineCapabilitiesTest" --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

- [ ] **Step 3: Add AuthApi and wire models**

```kotlin
interface AuthApi {
    suspend fun bootstrapAnonymous(request: AnonymousBootstrapRequest): OnlineResult<AuthTokens>
    suspend fun refreshAuth(request: RefreshAuthRequest): OnlineResult<AuthTokens>
    suspend fun authMe(): OnlineResult<AuthIdentity>
    suspend fun authSessions(): OnlineResult<AuthSessionPage>
    suspend fun revokeAuthSession(sessionId: String): OnlineResult<Unit>
}

class HttpOnlineApi(
    baseUrl: String,
    private val transport: OnlineHttpTransport,
    private val authTokenProvider: OnlineAuthTokenProvider = OnlineAuthTokenProvider { null },
    private val requestIdFactory: () -> String = { "req_${UUID.randomUUID()}" }
) : OnlineApi, AuthApi
```

Keep `AuthApi` separate from `OnlineApi` in Slice 0 so existing contract fakes do not all require auth methods in the same change. Add an `AuthApi` property at app wiring later.

Keep deprecated Kotlin `userId` constructor fields temporarily where `core:data` callers still compile, but remove them from every protected request serializer. Mark them as compatibility-only and add tests proving they never enter JSON or ownership decisions. Preserve `userId` in server response models where it represents the authenticated account. A later cross-module cleanup can remove the deprecated constructor fields after all callers migrate.

- [ ] **Step 4: Decode canonical errors**

Extend `OnlineResult.Failure` with safe optional fields:

```kotlin
data class Failure(
    val code: String,
    val retryable: Boolean,
    val userAction: String = "none",
    val requestId: String? = null
) : OnlineResult<Nothing>
```

Do not include raw response bodies.

Extend `OnlineHttpResponse` with a case-insensitive response-header map and make `UrlConnectionOnlineHttpTransport` preserve `X-Request-Id`. Add an injected request-ID factory to `HttpOnlineApi`; `X-Request-Id` must be independent from `idempotencyKey`.

- [ ] **Step 5: Run tests and verify GREEN**

Use the Task 6 Step 2 command.

- [ ] **Step 6: Commit Android contract support**

```powershell
git add mobile-native/core/remote
git commit -m "feat: add native anonymous auth api"
```

### Task 7: FastAPI And Remote Regression

**Files:**
- Verify only.

- [ ] **Step 1: Run Python online tests**

```powershell
py -m pytest tests/test_online_openapi_contract.py tests/test_online_error_contract.py tests/test_online_auth_store_contract.py tests/test_online_auth_service.py tests/test_online_auth_api.py tests/test_online_hybrid_api.py -v
```

Expected: all selected tests pass.

- [ ] **Step 2: Run Android remote tests and lint**

```powershell
mobile-native\gradlew.bat :core:remote:testDebugUnitTest :core:remote:lintDebug --no-daemon --max-workers=1 "-Pkotlin.incremental=false"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Scan for trusted body user IDs and secret logging**

```powershell
rg -n "request\.user_id|put\(\"userId\"|accessToken.*(print|log)|refreshToken.*(print|log)" adapters/online mobile-native/core/remote
```

Expected: no protected ownership path trusts `request.user_id`; no token logging exists.

- [ ] **Step 4: Inspect worker scope**

```powershell
git diff --check
git status --short
```

Expected: no Alembic, SQLAlchemy table, Compose UI, Room DAO or SecretStore edits from this plan.
