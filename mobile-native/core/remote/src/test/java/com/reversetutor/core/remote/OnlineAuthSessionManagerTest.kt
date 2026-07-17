package com.reversetutor.core.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class OnlineAuthSessionManagerTest {
    @Test
    fun authStateCodecRoundTripsAtomicCredentialState() {
        val state = authState(tokens = tokens()).copy(
            pendingRefreshIdempotencyKey = "refresh-request-0001"
        )

        assertEquals(state, OnlineAuthStateCodec.decode(OnlineAuthStateCodec.encode(state)))
    }

    @Test
    fun missingStateBootstrapsAndPersistsAnonymousCredentials() = runBlocking {
        val issued = tokens(deviceId = "device-installation-0001")
        val api = FakeAuthApi(bootstrapResults = listOf(OnlineResult.Success(issued)))
        val store = FakeOnlineAuthStateStore()
        val manager = OnlineAuthSessionManager(
            authApi = api,
            stateStore = store,
            appVersionCode = 7,
            nowEpochMillis = { 1_000L },
            deviceIdFactory = { "device-installation-0001" },
            idempotencyKeyFactory = { "bootstrap-request-0001" }
        )

        assertEquals("access-token-1", manager.token())
        assertEquals(
            AnonymousBootstrapRequest(
                deviceId = "device-installation-0001",
                idempotencyKey = "bootstrap-request-0001",
                appVersionCode = 7
            ),
            api.bootstrapRequests.single()
        )
        assertEquals(issued, store.state?.tokens)
        assertEquals(2, store.writes.size)
    }

    @Test
    fun unexpiredAccessTokenDoesNotCallAuthApi() = runBlocking {
        val state = authState(tokens = tokens())
        val store = FakeOnlineAuthStateStore(state)
        val api = FakeAuthApi()
        val manager = OnlineAuthSessionManager(
            authApi = api,
            stateStore = store,
            appVersionCode = 7,
            nowEpochMillis = { 1_000L }
        )

        assertEquals("access-token-1", manager.token())
        assertEquals(emptyList<AnonymousBootstrapRequest>(), api.bootstrapRequests)
        assertEquals(emptyList<RefreshAuthRequest>(), api.refreshRequests)
        assertSame(state, store.state)
    }

    @Test
    fun refreshRetryReusesPersistedIdempotencyKeyAndStoresRotation() = runBlocking {
        val original = tokens(accessExpiresAt = 1_030L, refreshExpiresAt = 20_000L)
        val rotated = tokens(
            accessToken = "access-token-2",
            refreshToken = "refresh-token-2-with-at-least-32-characters",
            accessExpiresAt = 10_000L,
            refreshExpiresAt = 30_000L
        )
        val store = FakeOnlineAuthStateStore(authState(tokens = original))
        val api = FakeAuthApi(
            refreshResults = listOf(
                OnlineResult.Failure("network_failure", retryable = true),
                OnlineResult.Success(rotated)
            )
        )
        val manager = OnlineAuthSessionManager(
            authApi = api,
            stateStore = store,
            appVersionCode = 7,
            nowEpochMillis = { 1_000L },
            idempotencyKeyFactory = { "refresh-request-0001" }
        )

        assertNull(manager.token())
        assertEquals("refresh-request-0001", store.state?.pendingRefreshIdempotencyKey)
        assertEquals("access-token-2", manager.token())
        assertEquals(2, api.refreshRequests.size)
        assertEquals(api.refreshRequests[0], api.refreshRequests[1])
        assertEquals(original.refreshToken, api.refreshRequests[0].refreshToken)
        assertEquals(rotated, store.state?.tokens)
        assertNull(store.state?.pendingRefreshIdempotencyKey)
    }

    @Test
    fun refreshBootstrapActionStartsANewAnonymousInstallation() = runBlocking {
        val original = tokens(accessExpiresAt = 1_000L, refreshExpiresAt = 20_000L)
        val replacement = tokens(
            deviceId = "device-installation-0002",
            accessToken = "access-token-2",
            refreshToken = "refresh-token-2-with-at-least-32-characters"
        )
        val store = FakeOnlineAuthStateStore(authState(tokens = original))
        val api = FakeAuthApi(
            bootstrapResults = listOf(OnlineResult.Success(replacement)),
            refreshResults = listOf(
                OnlineResult.Failure(
                    code = "invalid_refresh_token",
                    retryable = false,
                    userAction = "bootstrap_anonymous"
                )
            )
        )
        val idempotencyKeys = ArrayDeque(
            listOf("refresh-request-0001", "bootstrap-request-0002")
        )
        val manager = OnlineAuthSessionManager(
            authApi = api,
            stateStore = store,
            appVersionCode = 7,
            nowEpochMillis = { 1_000L },
            deviceIdFactory = { "device-installation-0002" },
            idempotencyKeyFactory = { idempotencyKeys.removeFirst() }
        )

        assertEquals("access-token-2", manager.token())
        assertEquals("device-installation-0002", api.bootstrapRequests.single().deviceId)
        assertEquals("bootstrap-request-0002", api.bootstrapRequests.single().idempotencyKey)
        assertEquals(replacement, store.state?.tokens)
    }

    @Test
    fun mismatchedBootstrapDeviceIsNotPersisted() = runBlocking {
        val api = FakeAuthApi(
            bootstrapResults = listOf(
                OnlineResult.Success(tokens(deviceId = "different-device-0001"))
            )
        )
        val store = FakeOnlineAuthStateStore()
        val manager = OnlineAuthSessionManager(
            authApi = api,
            stateStore = store,
            appVersionCode = 7,
            nowEpochMillis = { 1_000L },
            deviceIdFactory = { "device-installation-0001" },
            idempotencyKeyFactory = { "bootstrap-request-0001" }
        )

        assertNull(manager.token())
        assertNull(store.state?.tokens)
    }
}

private fun authState(tokens: AuthTokens?) = OnlineAuthState(
    deviceId = "device-installation-0001",
    bootstrapIdempotencyKey = "bootstrap-request-0001",
    bootstrapAppVersionCode = 7,
    tokens = tokens
)

private fun tokens(
    deviceId: String = "device-installation-0001",
    accessToken: String = "access-token-1",
    refreshToken: String = "refresh-token-1-with-at-least-32-characters",
    accessExpiresAt: Long = 100_000L,
    refreshExpiresAt: Long = 200_000L
) = AuthTokens(
    accountId = "account-1",
    deviceId = deviceId,
    sessionId = "session-1",
    tokenType = "Bearer",
    accessToken = accessToken,
    accessTokenExpiresAtEpochMillis = accessExpiresAt,
    refreshToken = refreshToken,
    refreshTokenExpiresAtEpochMillis = refreshExpiresAt
)

private class FakeOnlineAuthStateStore(
    initialState: OnlineAuthState? = null
) : OnlineAuthStateStore {
    var state: OnlineAuthState? = initialState
    val writes = mutableListOf<OnlineAuthState>()

    override suspend fun read(): OnlineAuthState? = state

    override suspend fun write(state: OnlineAuthState) {
        this.state = state
        writes += state
    }

    override suspend fun clear() {
        state = null
    }
}

private class FakeAuthApi(
    bootstrapResults: List<OnlineResult<AuthTokens>> = emptyList(),
    refreshResults: List<OnlineResult<AuthTokens>> = emptyList()
) : AuthApi {
    private val bootstrapResults = ArrayDeque(bootstrapResults)
    private val refreshResults = ArrayDeque(refreshResults)
    val bootstrapRequests = mutableListOf<AnonymousBootstrapRequest>()
    val refreshRequests = mutableListOf<RefreshAuthRequest>()

    override suspend fun bootstrapAnonymous(
        request: AnonymousBootstrapRequest
    ): OnlineResult<AuthTokens> {
        bootstrapRequests += request
        return bootstrapResults.removeFirst()
    }

    override suspend fun refreshAuth(request: RefreshAuthRequest): OnlineResult<AuthTokens> {
        refreshRequests += request
        return refreshResults.removeFirst()
    }

    override suspend fun authMe(): OnlineResult<AuthIdentity> = error("Not used")

    override suspend fun authSessions(): OnlineResult<AuthSessionPage> = error("Not used")

    override suspend fun revokeAuthSession(sessionId: String): OnlineResult<Unit> = error("Not used")
}
