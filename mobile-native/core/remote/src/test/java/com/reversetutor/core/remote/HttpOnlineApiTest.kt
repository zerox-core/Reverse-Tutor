package com.reversetutor.core.remote

import com.reversetutor.core.model.SyncEnvelope
import com.reversetutor.core.model.SyncOwnership
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpOnlineApiTest {
    @Test
    fun contentFeedUsesCanonicalQueryHeadersAndDecodesItems() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                200,
                """{"version":28,"updatedAtEpochMillis":100,"items":[{"id":"public-028","slug":"verify-links","type":"public_interest","title":"Verify links","summary":"Pause first","illustrationTemplate":"dialogue-security-01","illustrationConfig":{"dialogues":["Safe?"],"palette":"cool-blue-amber"},"cover":null,"publisherName":"Editors","publishedAtEpochMillis":90,"contentVersion":2}],"nextCursor":"next-2"}"""
            )
        )
        val api = HttpOnlineApi("https://online.example", transport)

        val result = api.contentFeed(
            cursor = "next",
            limit = 10,
            types = setOf("public_interest", "announcement"),
            etag = "feed-v28"
        )

        val page = (result as OnlineResult.Success).value
        assertEquals("public-028", page.items.single().id)
        assertEquals(listOf("Safe?"), page.items.single().illustration.dialogues)
        assertEquals("next-2", page.nextCursor)
        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals(
            "https://online.example/api/v1/content/feed?cursor=next&limit=10&types=announcement%2Cpublic_interest",
            request.url
        )
        assertEquals("feed-v28", request.headers["If-None-Match"])
    }

    @Test
    fun contentDetailDecodesBodyAssets() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                200,
                """{"id":"public-028","slug":"verify/links","type":"public_interest","title":"Verify links","summary":"Pause first","illustrationTemplate":"dialogue-security-01","illustrationConfig":{},"cover":null,"publisherName":null,"publishedAtEpochMillis":90,"contentVersion":2,"bodyMarkdown":"## Pause","bodyAssets":[{"url":"https://cdn.example/a.webp","mimeType":"image/webp","width":800,"height":600,"bytes":1200,"sha256":"abc"}]}"""
            )
        )
        val api = HttpOnlineApi("https://online.example", transport)

        val result = api.contentDetail("verify/links")

        val detail = (result as OnlineResult.Success).value
        assertEquals("## Pause", detail.bodyMarkdown)
        assertEquals(800, detail.bodyAssets.single().width)
        assertEquals(
            "https://online.example/api/v1/content/verify%2Flinks",
            transport.requests.single().url
        )
    }

    @Test
    fun activityRequestsUseConfiguredUrlAuthAndDecodeDtos() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                200,
                """{"items":[{"id":"focus-week","title":"Focus Week","description":"Explain daily","revision":2,"startsAtEpochMillis":10,"endsAtEpochMillis":20,"requiresOnlineConfirmation":false,"allowsDeferredProgress":true,"state":"active","sessionTemplateId":"focus-v1"}],"nextCursor":"page-2","updatedAtEpochMillis":30}"""
            )
        )
        val api = HttpOnlineApi(
            baseUrl = "https://online.example/",
            transport = transport,
            authTokenProvider = OnlineAuthTokenProvider { "session-token" }
        )

        val result = api.listActivities(cursor = "page-1", limit = 10)

        val page = (result as OnlineResult.Success).value
        assertEquals("focus-week", page.items.single().id)
        assertEquals("Explain daily", page.items.single().description)
        assertEquals("focus-v1", page.items.single().sessionTemplateId)
        assertEquals("page-2", page.nextCursor)
        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals("https://online.example/api/v1/activities?cursor=page-1&limit=10", request.url)
        assertFalse(request.headers.containsKey("Authorization"))
        assertNull(request.body)
    }

    @Test
    fun activityWritesEncodeIdentityAndProgress() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                200,
                """{"activityId":"focus/week","userId":"user-1","joined":true,"progress":3,"revision":4,"state":"joined","idempotencyKey":"write-1"}"""
            )
        )
        val api = authenticatedApi(transport)

        val result = api.updateActivityProgress(
            activityId = "focus/week",
            write = OnlineWriteIdentity("user-1", "device-1", 2, "write-1"),
            progress = 3
        )

        assertEquals(
            OnlineResult.Success(
                ActivityProgress("focus/week", "user-1", true, 3, 4, "joined", "write-1")
            ),
            result
        )
        val request = transport.requests.single()
        assertEquals(
            "https://online.example/api/v1/activities/focus%2Fweek/progress",
            request.url
        )
        assertTrue(request.body.orEmpty().contains(""""idempotencyKey":"write-1""""))
        assertTrue(request.body.orEmpty().contains(""""progress":3"""))
        assertFalse(request.body.orEmpty().contains("userId"))
        assertFalse(request.headers["X-Request-Id"] == "write-1")
        assertEquals("Bearer session-token", request.headers["Authorization"])
    }

    @Test
    fun leaveAndLeaderboardUseCanonicalActivityContract() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                200,
                """{"activityId":"focus-week","userId":"user-1","joined":false,"progress":3,"revision":5,"state":"left","idempotencyKey":"leave-1"}"""
            ),
            OnlineHttpResponse(
                200,
                """{"items":[{"rank":1,"displayName":"Learner","avatarUrl":null,"progress":7,"isCurrentUser":true}],"nextCursor":null,"updatedAtEpochMillis":40}"""
            )
        )
        val api = authenticatedApi(transport)

        val left = api.leaveActivity(
            "focus-week",
            OnlineWriteIdentity("user-1", "device-1", 4, "leave-1")
        )
        val leaderboard = api.activityLeaderboard("focus-week", cursor = "rank-1", limit = 25)

        assertFalse((left as OnlineResult.Success).value.joined)
        val ranking = (leaderboard as OnlineResult.Success).value
        assertEquals("Learner", ranking.items.single().displayName)
        assertTrue(ranking.items.single().isCurrentUser)
        assertEquals(
            "https://online.example/api/v1/activities/focus-week/participation",
            transport.requests[0].url
        )
        assertEquals("DELETE", transport.requests[0].method)
        assertEquals(
            "https://online.example/api/v1/activities/focus-week/leaderboard?cursor=rank-1&limit=25",
            transport.requests[1].url
        )
    }

    @Test
    fun syncPushEncodesPayloadAndDecodesPerItemResult() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                200,
                """{"cursor":"7","items":[{"envelopeId":"envelope-1","entityId":"plan-1","accepted":true,"remoteRevision":8,"errorCode":null,"retryable":false}]}"""
            )
        )
        val api = authenticatedApi(transport)
        val envelope = envelope(payload = """{"completed":true}""")

        val result = api.pushSync(
            SyncPushRequest("user-1", "device-1", listOf(envelope))
        )

        assertEquals(
            OnlineResult.Success(
                SyncPushResponse(
                    cursor = "7",
                    items = listOf(
                        SyncPushItemResult(
                            envelopeId = "envelope-1",
                            entityId = "plan-1",
                            accepted = true,
                            remoteRevision = 8,
                            retryable = false
                        )
                    )
                )
            ),
            result
        )
        val body = transport.requests.single().body.orEmpty()
        assertFalse(body.contains("userId"))
        assertTrue(body.contains(""""envelopeId":"envelope-1""""))
        assertTrue(body.contains(""""entityType":"study_plan""""))
        assertTrue(body.contains(""""payload":{"completed":true}"""))
    }

    @Test
    fun syncPushRejectsLegacyStatusResponse() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                200,
                """{"cursor":"7","items":[{"entityId":"plan-1","status":"accepted","remoteRevision":8,"retryable":false}]}"""
            )
        )
        val api = authenticatedApi(transport)

        val result = api.pushSync(
            SyncPushRequest("user-1", "device-1", listOf(envelope()))
        )

        assertEquals(OnlineResult.Failure("protocol_error", false), result)
    }

    @Test
    fun invalidSyncPayloadFailsBeforeTransportExecution() = runBlocking {
        val transport = FakeTransport()
        val api = HttpOnlineApi("https://online.example", transport)

        val result = api.pushSync(
            SyncPushRequest(
                "user-1",
                "device-1",
                listOf(envelope(payload = "not-json"))
            )
        )

        assertEquals(OnlineResult.Failure("protocol_error", false), result)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun disallowedSyncEntityFailsBeforeTransportExecution() = runBlocking {
        val transport = FakeTransport()
        val api = HttpOnlineApi("https://online.example", transport)

        val result = api.pushSync(
            SyncPushRequest(
                "user-1",
                "device-1",
                listOf(envelope().copy(entityType = "chat_message"))
            )
        )

        val response = (result as OnlineResult.Success).value
        assertEquals(listOf("envelope-1"), response.rejectedEnvelopeIds)
        assertEquals("entity_type_not_syncable", response.items.single().errorCode)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun pullWeeklyAndReleaseResponsesDecodeWithoutLeakingDtos() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                200,
                """{"cursor":"2","items":[{"entityId":"plan-1","entityType":"study_plan","ownerId":"user-1","deviceId":"device-2","revision":3,"idempotencyKey":"pull-1","payload":{"done":true},"deletedAtEpochMillis":null}]}"""
            ),
            OnlineHttpResponse(
                200,
                """{"spaceId":"space-1","weekStartEpochMillis":1000,"sourceRevision":4,"generatorVersion":"v1","summary":"Good week"}"""
            ),
            OnlineHttpResponse(
                200,
                """{"versionName":"2.0","versionCode":20,"minimumSupportedVersionCode":10,"downloadUrl":null,"sha256":"abc"}"""
            )
        )
        val api = authenticatedApi(transport)

        val pull = api.pullSync(SyncPullRequest("user-1", "device-1", spaceId = "space-1"))
        val insight = api.weeklyInsight(
            WeeklyInsightRequest("user-1", "device-1", "space-1", 1000, 4, mapOf("activeDays" to 3))
        )
        val release = api.latestRelease()

        val pulled = (pull as OnlineResult.Success).value.items.single()
        assertEquals("study_plan", pulled.entityType)
        assertEquals("space-1", pulled.spaceId)
        assertEquals("""{"done":true}""", pulled.payload)
        assertEquals(OnlineResult.Success(WeeklyInsight("space-1", 1000, 4, "Good week")), insight)
        assertFalse(transport.requests[0].body.orEmpty().contains("userId"))
        assertFalse(transport.requests[1].body.orEmpty().contains("userId"))
        assertEquals(
            OnlineResult.Success(OnlineRelease("2.0", 20, 10, null, "abc")),
            release
        )
    }

    @Test
    fun httpTransportAndProtocolFailuresHaveStableRetryability() = runBlocking {
        val statuses = listOf(
            401 to OnlineResult.Failure("unauthorized", false),
            403 to OnlineResult.Failure("forbidden", false),
            404 to OnlineResult.Failure("not_found", false),
            408 to OnlineResult.Failure("timeout", true),
            429 to OnlineResult.Failure("rate_limited", true),
            503 to OnlineResult.Failure("server_error", true)
        )

        statuses.forEach { (status, expected) ->
            val api = HttpOnlineApi(
                "https://online.example",
                FakeTransport(OnlineHttpResponse(status, """{"detail":"failure"}"""))
            )
            assertEquals(expected, api.latestRelease())
        }

        val timeoutApi = HttpOnlineApi(
            "https://online.example",
            OnlineHttpTransport { throw OnlineTransportException("timeout", retryable = true) }
        )
        assertEquals(OnlineResult.Failure("timeout", true), timeoutApi.latestRelease())

        val malformedApi = HttpOnlineApi(
            "https://online.example",
            FakeTransport(OnlineHttpResponse(200, """{"versionCode":"wrong"}"""))
        )
        assertEquals(
            OnlineResult.Failure("protocol_error", false),
            malformedApi.latestRelease()
        )

        val authFailureApi = HttpOnlineApi(
            "https://online.example",
            FakeTransport(),
            OnlineAuthTokenProvider { error("credential store unavailable") }
        )
        assertEquals(
            OnlineResult.Failure("auth_token_unavailable", false),
            authFailureApi.authMe()
        )
    }

    @Test
    fun canonicalErrorEnvelopePreservesBackendCodeAndRetryability() = runBlocking {
        val api = HttpOnlineApi(
            "https://online.example",
            FakeTransport(
                OnlineHttpResponse(
                    409,
                    """{"error":{"code":"revision_conflict","message":"Remote is newer","retryable":false,"requestId":"req-1"}}"""
                )
            )
        )

        assertEquals(
            OnlineResult.Failure(
                "revision_conflict",
                retryable = false,
                requestId = "req-1"
            ),
            api.latestRelease()
        )
    }

    @Test
    fun protectedRequestWithoutTokenFailsLocally() = runBlocking {
        val transport = FakeTransport()
        val api = HttpOnlineApi(
            "https://online.example",
            transport,
            authTokenProvider = OnlineAuthTokenProvider { null }
        )

        assertEquals(
            OnlineResult.Failure("auth_token_unavailable", false),
            api.authMe()
        )
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun bootstrapIsPublicAndDecodesTokens() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                201,
                """{"accountId":"account-1","deviceId":"device-0123456789","sessionId":"session-1","tokenType":"Bearer","accessToken":"access-1","accessTokenExpiresAtEpochMillis":1000,"refreshToken":"refresh-token-with-at-least-32-characters","refreshTokenExpiresAtEpochMillis":2000}"""
            )
        )
        val api = HttpOnlineApi(
            "https://online.example",
            transport,
            authTokenProvider = OnlineAuthTokenProvider { "must-not-be-used" },
            requestIdFactory = { "req-bootstrap-1" }
        )

        val result = api.bootstrapAnonymous(
            AnonymousBootstrapRequest("device-0123456789", "bootstrap-1", 1)
        )

        assertEquals("account-1", (result as OnlineResult.Success).value.accountId)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://online.example/api/v1/auth/anonymous", request.url)
        assertEquals("req-bootstrap-1", request.headers["X-Request-Id"])
        assertFalse(request.headers.containsKey("Authorization"))
    }

    @Test
    fun refreshIsPublicAndDoesNotPutTokenInHeaders() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                200,
                """{"accountId":"account-1","deviceId":"device-0123456789","sessionId":"session-1","tokenType":"Bearer","accessToken":"access-2","accessTokenExpiresAtEpochMillis":1100,"refreshToken":"replacement-refresh-token-with-32-characters","refreshTokenExpiresAtEpochMillis":2100}"""
            )
        )
        val api = HttpOnlineApi(
            "https://online.example",
            transport,
            authTokenProvider = OnlineAuthTokenProvider { "must-not-be-used" }
        )

        val result = api.refreshAuth(
            RefreshAuthRequest(
                refreshToken = "presented-refresh-token-with-32-characters",
                idempotencyKey = "refresh-1"
            )
        )

        assertEquals("access-2", (result as OnlineResult.Success).value.accessToken)
        val request = transport.requests.single()
        assertFalse(request.headers.containsKey("Authorization"))
        assertTrue(request.body.orEmpty().contains("presented-refresh-token"))
    }

    @Test
    fun authQueriesAndRevokeDecodeCanonicalDtos() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                200,
                """{"accountId":"account-1","deviceId":"device-0123456789","sessionId":"session-1","accountType":"anonymous","sessionExpiresAtEpochMillis":2000}"""
            ),
            OnlineHttpResponse(
                200,
                """{"items":[{"sessionId":"session-1","deviceId":"device-0123456789","status":"active","createdAtEpochMillis":1000,"expiresAtEpochMillis":2000,"current":true}]}"""
            ),
            OnlineHttpResponse(204, headers = mapOf("x-request-id" to "req-revoke-1"))
        )
        val api = HttpOnlineApi(
            "https://online.example",
            transport,
            authTokenProvider = OnlineAuthTokenProvider { "access-1" }
        )

        val me = api.authMe()
        val sessions = api.authSessions()
        val revoked = api.revokeAuthSession("session-1")

        assertEquals("account-1", (me as OnlineResult.Success).value.accountId)
        assertTrue((sessions as OnlineResult.Success).value.items.single().current)
        assertEquals(OnlineResult.Success(Unit), revoked)
        assertTrue(transport.requests.all { it.headers["Authorization"] == "Bearer access-1" })
        assertEquals("DELETE", transport.requests.last().method)
    }

    @Test
    fun canonicalErrorPreservesUserActionAndCaseInsensitiveRequestId() = runBlocking {
        val api = HttpOnlineApi(
            "https://online.example",
            FakeTransport(
                OnlineHttpResponse(
                    statusCode = 401,
                    body = """{"error":{"code":"unauthorized","message":"Authentication is required","retryable":false,"userAction":"bootstrap_anonymous","requestId":"req-auth-1","details":{}}}""",
                    headers = mapOf("x-ReQuEsT-iD" to "req-auth-1")
                )
            ),
            authTokenProvider = OnlineAuthTokenProvider { "expired-access" }
        )

        assertEquals(
            OnlineResult.Failure(
                code = "unauthorized",
                retryable = false,
                userAction = "bootstrap_anonymous",
                requestId = "req-auth-1"
            ),
            api.authMe()
        )
    }

    private fun envelope(payload: String? = null) = SyncEnvelope(
        id = "envelope-1",
        spaceId = "space-1",
        entityId = "plan-1",
        entityType = "study_plan",
        ownerId = "user-1",
        deviceId = "device-1",
        revision = 7,
        idempotencyKey = "sync-1",
        ownership = SyncOwnership.Shared,
        payload = payload
    )
}

private fun authenticatedApi(transport: OnlineHttpTransport): HttpOnlineApi =
    HttpOnlineApi(
        "https://online.example",
        transport,
        authTokenProvider = OnlineAuthTokenProvider { "session-token" }
    )

private class FakeTransport(
    vararg responses: OnlineHttpResponse
) : OnlineHttpTransport {
    private val responses = ArrayDeque(responses.toList())
    val requests = mutableListOf<OnlineHttpRequest>()

    override suspend fun execute(request: OnlineHttpRequest): OnlineHttpResponse {
        requests += request
        return responses.removeFirst()
    }
}
