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
    fun activityRequestsUseConfiguredUrlAuthAndDecodeDtos() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                200,
                """{"items":[{"id":"focus-week","title":"Focus Week","revision":2,"startsAtEpochMillis":10,"endsAtEpochMillis":20}]}"""
            )
        )
        val api = HttpOnlineApi(
            baseUrl = "https://online.example/",
            transport = transport,
            authTokenProvider = OnlineAuthTokenProvider { "session-token" }
        )

        val result = api.listActivities()

        assertEquals(
            OnlineResult.Success(
                listOf(OnlineActivity("focus-week", "Focus Week", 2, 10, 20))
            ),
            result
        )
        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals("https://online.example/api/v1/activities", request.url)
        assertEquals("Bearer session-token", request.headers["Authorization"])
        assertNull(request.body)
    }

    @Test
    fun activityWritesEncodeIdentityAndProgress() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                200,
                """{"activityId":"focus/week","userId":"user-1","progress":3,"revision":4}"""
            )
        )
        val api = HttpOnlineApi("https://online.example", transport)

        val result = api.updateActivityProgress(
            activityId = "focus/week",
            write = OnlineWriteIdentity("user-1", "device-1", 2, "write-1"),
            progress = 3
        )

        assertEquals(
            OnlineResult.Success(ActivityProgress("focus/week", "user-1", 3, 4)),
            result
        )
        val request = transport.requests.single()
        assertEquals(
            "https://online.example/api/v1/activities/focus%2Fweek/progress",
            request.url
        )
        assertTrue(request.body.orEmpty().contains(""""idempotencyKey":"write-1""""))
        assertTrue(request.body.orEmpty().contains(""""progress":3"""))
        assertFalse(request.headers.containsKey("Authorization"))
    }

    @Test
    fun syncPushEncodesPayloadAndDecodesPerItemResult() = runBlocking {
        val transport = FakeTransport(
            OnlineHttpResponse(
                200,
                """{"cursor":"7","items":[{"envelopeId":"envelope-1","entityId":"plan-1","accepted":true,"remoteRevision":8,"errorCode":null,"retryable":false}]}"""
            )
        )
        val api = HttpOnlineApi("https://online.example", transport)
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
        val api = HttpOnlineApi("https://online.example", transport)

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
        val api = HttpOnlineApi("https://online.example", transport)

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
            authFailureApi.latestRelease()
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
