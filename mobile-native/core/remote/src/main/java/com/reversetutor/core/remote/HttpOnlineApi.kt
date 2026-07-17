package com.reversetutor.core.remote

import com.reversetutor.core.model.SyncEnvelope
import com.reversetutor.core.model.SyncOperation
import com.reversetutor.core.model.SyncOwnership
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class HttpOnlineApi(
    baseUrl: String,
    private val transport: OnlineHttpTransport,
    private val authTokenProvider: OnlineAuthTokenProvider = OnlineAuthTokenProvider { null },
    private val requestIdFactory: () -> String = { "req_${UUID.randomUUID()}" }
) : OnlineApi, AuthApi {
    private val baseUrl = baseUrl.trim().trimEnd('/')
    private val json: Json = Json { ignoreUnknownKeys = true }

    init {
        require(this.baseUrl.startsWith("http://") || this.baseUrl.startsWith("https://")) {
            "Online API base URL must use HTTP or HTTPS"
        }
    }

    override suspend fun bootstrapAnonymous(
        request: AnonymousBootstrapRequest
    ): OnlineResult<AuthTokens> =
        post(
            path = "/api/v1/auth/anonymous",
            body = request.toJson(),
            requiresAuth = false
        ) { it.toAuthTokens() }

    override suspend fun refreshAuth(
        request: RefreshAuthRequest
    ): OnlineResult<AuthTokens> =
        post(
            path = "/api/v1/auth/refresh",
            body = request.toJson(),
            requiresAuth = false
        ) { it.toAuthTokens() }

    override suspend fun authMe(): OnlineResult<AuthIdentity> =
        get("/api/v1/auth/me") { body ->
            AuthIdentity(
                accountId = body.requiredString("accountId"),
                deviceId = body.requiredString("deviceId"),
                sessionId = body.requiredString("sessionId"),
                accountType = body.requiredString("accountType"),
                sessionExpiresAtEpochMillis = body.requiredLong("sessionExpiresAtEpochMillis")
            )
        }

    override suspend fun authSessions(): OnlineResult<AuthSessionPage> =
        get("/api/v1/auth/sessions") { body ->
            AuthSessionPage(
                items = body.requiredArray("items").map { item ->
                    item.jsonObject.toAuthSession()
                }
            )
        }

    override suspend fun revokeAuthSession(sessionId: String): OnlineResult<Unit> =
        execute(
            method = "DELETE",
            path = "/api/v1/auth/sessions/${sessionId.pathSegment()}",
            body = null,
            extraHeaders = emptyMap(),
            requiresAuth = true
        ) { Unit }

    override suspend fun contentFeed(
        cursor: String?,
        limit: Int,
        types: Set<String>,
        etag: String?
    ): OnlineResult<ContentFeedPage> {
        require(limit in 1..50) { "Content feed limit must be between 1 and 50" }
        val query = buildList {
            cursor?.let { add("cursor=${it.queryValue()}") }
            add("limit=$limit")
            if (types.isNotEmpty()) add("types=${types.sorted().joinToString(",").queryValue()}")
        }.joinToString("&")
        val headers = etag?.let { mapOf("If-None-Match" to it) }.orEmpty()
        return get("/api/v1/content/feed?$query", headers, requiresAuth = false) { body ->
            ContentFeedPage(
                version = body.requiredLong("version"),
                updatedAtEpochMillis = body.requiredLong("updatedAtEpochMillis"),
                items = body.requiredArray("items").map { it.jsonObject.toContentItem() },
                nextCursor = body.optionalString("nextCursor")
            )
        }
    }

    override suspend fun contentDetail(slug: String): OnlineResult<OnlineContentDetail> =
        get("/api/v1/content/${slug.pathSegment()}", requiresAuth = false) { body ->
            OnlineContentDetail(
                item = body.toContentItem(),
                bodyMarkdown = body.requiredString("bodyMarkdown"),
                bodyAssets = body.requiredArray("bodyAssets").map { it.jsonObject.toAssetRef() }
            )
        }

    override suspend fun listActivities(cursor: String?, limit: Int): OnlineResult<ActivityPage> {
        require(limit in 1..50) { "Activity limit must be between 1 and 50" }
        val query = buildList {
            cursor?.let { add("cursor=${it.queryValue()}") }
            add("limit=$limit")
        }.joinToString("&")
        return get("/api/v1/activities?$query", requiresAuth = false) { body ->
            ActivityPage(
                items = body.requiredArray("items").map { it.jsonObject.toActivity() },
                nextCursor = body.optionalString("nextCursor"),
                updatedAtEpochMillis = body.requiredLong("updatedAtEpochMillis")
            )
        }
    }

    override suspend fun getActivity(activityId: String): OnlineResult<OnlineActivity> =
        get(
            "/api/v1/activities/${activityId.pathSegment()}",
            requiresAuth = false
        ) { it.toActivity() }

    override suspend fun activityLeaderboard(
        activityId: String,
        cursor: String?,
        limit: Int
    ): OnlineResult<LeaderboardPage> {
        require(limit in 1..100) { "Leaderboard limit must be between 1 and 100" }
        val query = buildList {
            cursor?.let { add("cursor=${it.queryValue()}") }
            add("limit=$limit")
        }.joinToString("&")
        return get(
            "/api/v1/activities/${activityId.pathSegment()}/leaderboard?$query",
            requiresAuth = false
        ) { body ->
            LeaderboardPage(
                items = body.requiredArray("items").map { it.jsonObject.toLeaderboardItem() },
                nextCursor = body.optionalString("nextCursor"),
                updatedAtEpochMillis = body.requiredLong("updatedAtEpochMillis")
            )
        }
    }

    override suspend fun joinActivity(
        activityId: String,
        write: OnlineWriteIdentity
    ): OnlineResult<ActivityProgress> =
        post(
            "/api/v1/activities/${activityId.pathSegment()}/join",
            write.toJson()
        ) { it.toActivityProgress() }

    override suspend fun updateActivityProgress(
        activityId: String,
        write: OnlineWriteIdentity,
        progress: Long
    ): OnlineResult<ActivityProgress> =
        post(
            "/api/v1/activities/${activityId.pathSegment()}/progress",
            write.toJson(progress)
        ) { it.toActivityProgress() }

    override suspend fun leaveActivity(
        activityId: String,
        write: OnlineWriteIdentity
    ): OnlineResult<ActivityProgress> =
        delete(
            "/api/v1/activities/${activityId.pathSegment()}/participation",
            write.toJson()
        ) { it.toActivityProgress() }

    override suspend fun pushSync(request: SyncPushRequest): OnlineResult<SyncPushResponse> {
        val rejected = request.items.filterNot { it.isSyncable() }.map {
            SyncPushItemResult(
                envelopeId = it.id,
                entityId = it.entityId,
                accepted = false,
                errorCode = if (it.ownership != SyncOwnership.Shared) {
                    "ownership_not_syncable"
                } else {
                    "entity_type_not_syncable"
                },
                retryable = false
            )
        }
        val syncableItems = request.items.filter { it.isSyncable() }
        if (syncableItems.isEmpty()) {
            return OnlineResult.Success(
                SyncPushResponse(cursor = request.cursor, items = rejected)
            )
        }
        val body = try {
            request.copy(items = syncableItems).toJson(json)
        } catch (_: RuntimeException) {
            return OnlineResult.Failure("protocol_error", retryable = false)
        }
        return post("/api/v1/sync/push", body) { response ->
            val items = response.requiredArray("items").map { itemValue ->
                val item = itemValue.jsonObject
                SyncPushItemResult(
                    envelopeId = item.requiredString("envelopeId"),
                    entityId = item.requiredString("entityId"),
                    accepted = item.requiredBoolean("accepted"),
                    remoteRevision = item.optionalLong("remoteRevision"),
                    errorCode = item.optionalString("errorCode"),
                    retryable = item.requiredBoolean("retryable")
                )
            } + rejected
            SyncPushResponse(
                cursor = response.optionalString("cursor"),
                items = items
            )
        }
    }

    override suspend fun pullSync(request: SyncPullRequest): OnlineResult<SyncPullResponse> =
        post("/api/v1/sync/pull", request.toJson()) { response ->
            val items = response.requiredArray("items").map { itemValue ->
                val item = itemValue.jsonObject
                val entityId = item.requiredString("entityId")
                val entityType = item.requiredString("entityType")
                val revision = item.requiredLong("revision")
                val deletedAt = item.optionalLong("deletedAtEpochMillis")
                SyncEnvelope(
                    id = "remote:$entityType:$entityId:$revision",
                    spaceId = request.spaceId,
                    entityId = entityId,
                    entityType = entityType,
                    ownerId = item.requiredString("ownerId"),
                    deviceId = item.requiredString("deviceId"),
                    revision = revision,
                    idempotencyKey = item.requiredString("idempotencyKey"),
                    ownership = SyncOwnership.Shared,
                    operation = if (deletedAt == null) SyncOperation.Upsert else SyncOperation.Delete,
                    payload = item.optionalObject("payload")?.toString(),
                    deletedAtEpochMillis = deletedAt
                )
            }.filter { OnlineSyncEntityTypes.isAllowed(it.entityType) }
            SyncPullResponse(
                cursor = response.optionalString("cursor"),
                items = items
            )
        }

    override suspend fun weeklyInsight(
        request: WeeklyInsightRequest
    ): OnlineResult<WeeklyInsight> =
        post("/api/v1/insights/weekly", request.toJson()) { body ->
            WeeklyInsight(
                spaceId = body.requiredString("spaceId"),
                weekStartEpochMillis = body.requiredLong("weekStartEpochMillis"),
                sourceRevision = body.requiredLong("sourceRevision"),
                summary = body.requiredString("summary")
            )
        }

    override suspend fun latestRelease(): OnlineResult<OnlineRelease> =
        get("/api/v1/app/releases/latest", requiresAuth = false) { body ->
            OnlineRelease(
                versionName = body.requiredString("versionName"),
                versionCode = body.requiredLong("versionCode"),
                minimumSupportedVersionCode = body.requiredLong("minimumSupportedVersionCode"),
                downloadUrl = body.optionalString("downloadUrl"),
                sha256 = body.optionalString("sha256")
            )
        }

    private suspend fun <T> get(
        path: String,
        headers: Map<String, String> = emptyMap(),
        requiresAuth: Boolean = true,
        decode: (JsonObject) -> T
    ): OnlineResult<T> = execute("GET", path, null, headers, requiresAuth) { body ->
        decode(json.parseToJsonElement(body).jsonObject)
    }

    private suspend fun <T> post(
        path: String,
        body: JsonObject,
        headers: Map<String, String> = emptyMap(),
        requiresAuth: Boolean = true,
        decode: (JsonObject) -> T
    ): OnlineResult<T> = execute(
        "POST",
        path,
        body.toString(),
        headers,
        requiresAuth
    ) { responseBody ->
        decode(json.parseToJsonElement(responseBody).jsonObject)
    }

    private suspend fun <T> delete(
        path: String,
        body: JsonObject,
        headers: Map<String, String> = emptyMap(),
        requiresAuth: Boolean = true,
        decode: (JsonObject) -> T
    ): OnlineResult<T> = execute(
        "DELETE",
        path,
        body.toString(),
        headers,
        requiresAuth
    ) { responseBody ->
        decode(json.parseToJsonElement(responseBody).jsonObject)
    }

    private suspend fun <T> execute(
        method: String,
        path: String,
        body: String?,
        extraHeaders: Map<String, String>,
        requiresAuth: Boolean,
        decode: (String) -> T
    ): OnlineResult<T> {
        val headers = linkedMapOf("Accept" to "application/json")
        headers.putAll(extraHeaders)
        if (body != null) headers["Content-Type"] = "application/json"
        val requestId = try {
            requestIdFactory()
        } catch (_: Throwable) {
            return OnlineResult.Failure("request_id_unavailable", retryable = false)
        }
        if (requestId.isBlank() || requestId.length > 128) {
            return OnlineResult.Failure("protocol_error", retryable = false)
        }
        headers["X-Request-Id"] = requestId
        if (requiresAuth) {
            val authToken = try {
                authTokenProvider.token()
            } catch (_: Throwable) {
                return OnlineResult.Failure("auth_token_unavailable", retryable = false)
            }
            val token = authToken?.trim()?.takeIf { it.isNotEmpty() }
                ?: return OnlineResult.Failure("auth_token_unavailable", retryable = false)
            headers["Authorization"] = "Bearer $token"
        }

        val response = try {
            transport.execute(
                OnlineHttpRequest(
                    method = method,
                    url = "$baseUrl$path",
                    headers = headers,
                    body = body
                )
            )
        } catch (error: OnlineTransportException) {
            return OnlineResult.Failure(error.errorCode, error.retryable)
        } catch (_: Throwable) {
            return OnlineResult.Failure("network_failure", retryable = true)
        }

        if (response.statusCode !in 200..299) {
            return response.toFailure(json)
        }
        return try {
            OnlineResult.Success(decode(response.body))
        } catch (_: RuntimeException) {
            OnlineResult.Failure("protocol_error", retryable = false)
        }
    }
}

private fun OnlineHttpResponse.toFailure(json: Json): OnlineResult.Failure {
    val canonical = try {
        json.parseToJsonElement(body).jsonObject.optionalObject("error")
    } catch (_: RuntimeException) {
        null
    }
    val code = canonical?.optionalString("code")
    if (!code.isNullOrBlank()) {
        return OnlineResult.Failure(
            code = code,
            retryable = canonical.optionalBoolean("retryable") ?: false,
            userAction = canonical.optionalString("userAction") ?: "none",
            requestId = canonical.optionalString("requestId") ?: header("X-Request-Id")
        )
    }
    return statusCode.toStatusFailure(header("X-Request-Id"))
}

private fun Int.toStatusFailure(requestId: String?): OnlineResult.Failure = when (this) {
    401 -> OnlineResult.Failure("unauthorized", retryable = false, requestId = requestId)
    403 -> OnlineResult.Failure("forbidden", retryable = false, requestId = requestId)
    404 -> OnlineResult.Failure("not_found", retryable = false, requestId = requestId)
    408 -> OnlineResult.Failure("timeout", retryable = true, requestId = requestId)
    429 -> OnlineResult.Failure("rate_limited", retryable = true, requestId = requestId)
    in 500..599 -> OnlineResult.Failure("server_error", retryable = true, requestId = requestId)
    else -> OnlineResult.Failure("http_$this", retryable = false, requestId = requestId)
}

private fun String.pathSegment(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun String.queryValue(): String = pathSegment()

private fun SyncEnvelope.isSyncable(): Boolean =
    ownership == SyncOwnership.Shared && OnlineSyncEntityTypes.isAllowed(entityType)

private fun AnonymousBootstrapRequest.toJson(): JsonObject = buildJsonObject {
    put("deviceId", deviceId)
    put("idempotencyKey", idempotencyKey)
    appVersionCode?.let { put("appVersionCode", it) }
}

private fun RefreshAuthRequest.toJson(): JsonObject = buildJsonObject {
    put("refreshToken", refreshToken)
    put("idempotencyKey", idempotencyKey)
}

private fun JsonObject.toAuthTokens(): AuthTokens = AuthTokens(
    accountId = requiredString("accountId"),
    deviceId = requiredString("deviceId"),
    sessionId = requiredString("sessionId"),
    tokenType = requiredString("tokenType"),
    accessToken = requiredString("accessToken"),
    accessTokenExpiresAtEpochMillis = requiredLong("accessTokenExpiresAtEpochMillis"),
    refreshToken = requiredString("refreshToken"),
    refreshTokenExpiresAtEpochMillis = requiredLong("refreshTokenExpiresAtEpochMillis")
)

private fun JsonObject.toAuthSession(): AuthSession = AuthSession(
    sessionId = requiredString("sessionId"),
    deviceId = requiredString("deviceId"),
    status = requiredString("status"),
    createdAtEpochMillis = requiredLong("createdAtEpochMillis"),
    expiresAtEpochMillis = requiredLong("expiresAtEpochMillis"),
    current = requiredBoolean("current")
)

private fun OnlineWriteIdentity.toJson(progress: Long? = null): JsonObject = buildJsonObject {
    put("deviceId", deviceId)
    put("revision", revision)
    put("idempotencyKey", idempotencyKey)
    progress?.let { put("progress", it) }
}

private fun SyncPushRequest.toJson(json: Json): JsonObject = buildJsonObject {
    put("deviceId", deviceId)
    putNullableString("cursor", cursor)
    put("items", buildJsonArray {
        items.forEach { envelope ->
            add(buildJsonObject {
                put("envelopeId", envelope.id)
                put("entityId", envelope.entityId)
                put("entityType", envelope.entityType)
                put("revision", envelope.revision)
                put("idempotencyKey", envelope.idempotencyKey)
                put("payload", envelope.payload.toPayload(json))
                putNullableLong("deletedAtEpochMillis", envelope.deletedAtEpochMillis)
            })
        }
    })
}

private fun SyncPullRequest.toJson(): JsonObject = buildJsonObject {
    put("deviceId", deviceId)
    putNullableString("cursor", cursor)
}

private fun WeeklyInsightRequest.toJson(): JsonObject = buildJsonObject {
    put("deviceId", deviceId)
    put("spaceId", spaceId)
    put("weekStartEpochMillis", weekStartEpochMillis)
    put("sourceRevision", sourceRevision)
    put("statistics", buildJsonObject {
        statistics.forEach { (key, value) -> put(key, value) }
    })
}

private fun String?.toPayload(json: Json): JsonObject {
    if (isNullOrBlank()) return JsonObject(emptyMap())
    return json.parseToJsonElement(this).jsonObject
}

private fun JsonObject.toActivity(): OnlineActivity = OnlineActivity(
    id = requiredString("id"),
    title = requiredString("title"),
    revision = requiredLong("revision"),
    startsAtEpochMillis = requiredLong("startsAtEpochMillis"),
    endsAtEpochMillis = requiredLong("endsAtEpochMillis"),
    description = requiredString("description"),
    requiresOnlineConfirmation = requiredBoolean("requiresOnlineConfirmation"),
    allowsDeferredProgress = requiredBoolean("allowsDeferredProgress"),
    state = requiredString("state"),
    sessionTemplateId = optionalString("sessionTemplateId")
)

private fun JsonObject.toActivityProgress(): ActivityProgress = ActivityProgress(
    activityId = requiredString("activityId"),
    userId = requiredString("userId"),
    joined = requiredBoolean("joined"),
    progress = requiredLong("progress"),
    revision = requiredLong("revision"),
    state = requiredString("state"),
    idempotencyKey = requiredString("idempotencyKey")
)

private fun JsonObject.toLeaderboardItem(): LeaderboardItem = LeaderboardItem(
    rank = requiredLong("rank"),
    displayName = requiredString("displayName"),
    avatarUrl = optionalString("avatarUrl"),
    progress = requiredLong("progress"),
    isCurrentUser = optionalBoolean("isCurrentUser") ?: false
)

private fun JsonObject.toContentItem(): OnlineContentItem = OnlineContentItem(
    id = requiredString("id"),
    slug = requiredString("slug"),
    type = requiredString("type"),
    title = requiredString("title"),
    summary = requiredString("summary"),
    illustrationTemplate = requiredString("illustrationTemplate"),
    illustration = requiredObject("illustrationConfig").let { config ->
        OnlineIllustrationConfig(
            dialogues = config.optionalArray("dialogues")
                ?.map { it.jsonPrimitive.content }
                .orEmpty(),
            palette = config.optionalString("palette")
        )
    },
    cover = optionalObject("cover")?.toAssetRef(),
    publisherName = optionalString("publisherName"),
    publishedAtEpochMillis = requiredLong("publishedAtEpochMillis"),
    contentVersion = requiredLong("contentVersion")
)

private fun JsonObject.toAssetRef(): OnlineAssetRef = OnlineAssetRef(
    url = requiredString("url"),
    mimeType = requiredString("mimeType"),
    width = requiredLong("width").toInt(),
    height = requiredLong("height").toInt(),
    bytes = optionalLong("bytes"),
    sha256 = optionalString("sha256")
)

private fun JsonObject.requiredString(name: String): String =
    getValue(name).jsonPrimitive.content

private fun JsonObject.optionalString(name: String): String? =
    get(name)?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

private fun JsonObject.requiredLong(name: String): Long =
    getValue(name).jsonPrimitive.longOrNull ?: error("Expected long: $name")

private fun JsonObject.optionalLong(name: String): Long? =
    get(name)?.takeUnless { it is JsonNull }?.jsonPrimitive?.longOrNull

private fun JsonObject.optionalBoolean(name: String): Boolean? =
    get(name)?.takeUnless { it is JsonNull }?.jsonPrimitive?.booleanOrNull

private fun JsonObject.requiredBoolean(name: String): Boolean =
    getValue(name).jsonPrimitive.booleanOrNull ?: error("Expected boolean: $name")

private fun JsonObject.requiredArray(name: String): JsonArray =
    getValue(name) as? JsonArray ?: error("Expected array: $name")

private fun JsonObject.optionalArray(name: String): JsonArray? =
    get(name)?.takeUnless { it is JsonNull } as? JsonArray

private fun JsonObject.requiredObject(name: String): JsonObject =
    getValue(name) as? JsonObject ?: error("Expected object: $name")

private fun JsonObject.optionalObject(name: String): JsonObject? =
    get(name)?.takeUnless { it is JsonNull } as? JsonObject

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableString(
    name: String,
    value: String?
) {
    put(name, value?.let(::JsonPrimitive) ?: JsonNull)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableLong(
    name: String,
    value: Long?
) {
    put(name, value?.let(::JsonPrimitive) ?: JsonNull)
}
