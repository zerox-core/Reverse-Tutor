package com.reversetutor.core.remote

import com.reversetutor.core.model.SyncEnvelope
import com.reversetutor.core.model.SyncOperation
import com.reversetutor.core.model.SyncOwnership
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
    private val json: Json = Json { ignoreUnknownKeys = true }
) : OnlineApi {
    private val baseUrl = baseUrl.trim().trimEnd('/')

    init {
        require(this.baseUrl.startsWith("http://") || this.baseUrl.startsWith("https://")) {
            "Online API base URL must use HTTP or HTTPS"
        }
    }

    override suspend fun listActivities(): OnlineResult<List<OnlineActivity>> =
        get("/api/v1/activities") { body ->
            body.requiredArray("items").map { it.jsonObject.toActivity() }
        }

    override suspend fun getActivity(activityId: String): OnlineResult<OnlineActivity> =
        get("/api/v1/activities/${activityId.pathSegment()}") { it.toActivity() }

    override suspend fun activityLeaderboard(
        activityId: String
    ): OnlineResult<List<ActivityProgress>> =
        get("/api/v1/activities/${activityId.pathSegment()}/leaderboard") { body ->
            body.requiredArray("items").map { it.jsonObject.toActivityProgress() }
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
            val envelopesByEntity = syncableItems.groupBy { it.entityId }
                .mapValues { (_, envelopes) -> ArrayDeque(envelopes) }
            val items = response.requiredArray("items").map { itemValue ->
                val item = itemValue.jsonObject
                val entityId = item.requiredString("entityId")
                val envelope = envelopesByEntity[entityId]?.removeFirstOrNull()
                val status = item.requiredString("status")
                SyncPushItemResult(
                    envelopeId = envelope?.id ?: entityId,
                    entityId = entityId,
                    accepted = status == "accepted",
                    remoteRevision = item.optionalLong("remoteRevision"),
                    errorCode = item.optionalString("errorCode"),
                    retryable = item.optionalBoolean("retryable") ?: false
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
        get("/api/v1/app/releases/latest") { body ->
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
        decode: (JsonObject) -> T
    ): OnlineResult<T> = execute("GET", path, null, decode)

    private suspend fun <T> post(
        path: String,
        body: JsonObject,
        decode: (JsonObject) -> T
    ): OnlineResult<T> = execute("POST", path, body.toString(), decode)

    private suspend fun <T> execute(
        method: String,
        path: String,
        body: String?,
        decode: (JsonObject) -> T
    ): OnlineResult<T> {
        val headers = linkedMapOf("Accept" to "application/json")
        if (body != null) headers["Content-Type"] = "application/json"
        val authToken = try {
            authTokenProvider.token()
        } catch (_: Throwable) {
            return OnlineResult.Failure("auth_token_unavailable", retryable = false)
        }
        authToken?.trim()?.takeIf { it.isNotEmpty() }?.let {
            headers["Authorization"] = "Bearer $it"
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
            return response.statusCode.toFailure()
        }
        return try {
            OnlineResult.Success(decode(json.parseToJsonElement(response.body).jsonObject))
        } catch (_: RuntimeException) {
            OnlineResult.Failure("protocol_error", retryable = false)
        }
    }
}

private fun Int.toFailure(): OnlineResult.Failure = when (this) {
    401 -> OnlineResult.Failure("unauthorized", retryable = false)
    403 -> OnlineResult.Failure("forbidden", retryable = false)
    404 -> OnlineResult.Failure("not_found", retryable = false)
    408 -> OnlineResult.Failure("timeout", retryable = true)
    429 -> OnlineResult.Failure("rate_limited", retryable = true)
    in 500..599 -> OnlineResult.Failure("server_error", retryable = true)
    else -> OnlineResult.Failure("http_$this", retryable = false)
}

private fun String.pathSegment(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun SyncEnvelope.isSyncable(): Boolean =
    ownership == SyncOwnership.Shared && OnlineSyncEntityTypes.isAllowed(entityType)

private fun OnlineWriteIdentity.toJson(progress: Long? = null): JsonObject = buildJsonObject {
    put("userId", userId)
    put("deviceId", deviceId)
    put("revision", revision)
    put("idempotencyKey", idempotencyKey)
    progress?.let { put("progress", it) }
}

private fun SyncPushRequest.toJson(json: Json): JsonObject = buildJsonObject {
    put("userId", userId)
    put("deviceId", deviceId)
    putNullableString("cursor", cursor)
    put("items", buildJsonArray {
        items.forEach { envelope ->
            add(buildJsonObject {
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
    put("userId", userId)
    put("deviceId", deviceId)
    putNullableString("cursor", cursor)
}

private fun WeeklyInsightRequest.toJson(): JsonObject = buildJsonObject {
    put("userId", userId)
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
    endsAtEpochMillis = requiredLong("endsAtEpochMillis")
)

private fun JsonObject.toActivityProgress(): ActivityProgress = ActivityProgress(
    activityId = requiredString("activityId"),
    userId = requiredString("userId"),
    progress = requiredLong("progress"),
    revision = requiredLong("revision")
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

private fun JsonObject.requiredArray(name: String): JsonArray =
    getValue(name) as? JsonArray ?: error("Expected array: $name")

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
