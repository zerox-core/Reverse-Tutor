package com.reversetutor.core.remote

import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class OnlineAuthState(
    val deviceId: String,
    val bootstrapIdempotencyKey: String,
    val bootstrapAppVersionCode: Long?,
    val tokens: AuthTokens? = null,
    val pendingRefreshIdempotencyKey: String? = null
)

interface OnlineAuthStateStore {
    suspend fun read(): OnlineAuthState?
    suspend fun write(state: OnlineAuthState)
    suspend fun clear()
}

object OnlineAuthStateCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(state: OnlineAuthState): String = buildJsonObject {
        put("schemaVersion", 1)
        put("deviceId", state.deviceId)
        put("bootstrapIdempotencyKey", state.bootstrapIdempotencyKey)
        putNullableLong("bootstrapAppVersionCode", state.bootstrapAppVersionCode)
        putNullableString(
            "pendingRefreshIdempotencyKey",
            state.pendingRefreshIdempotencyKey
        )
        state.tokens?.let { tokens ->
            put("tokens", buildJsonObject {
                put("accountId", tokens.accountId)
                put("deviceId", tokens.deviceId)
                put("sessionId", tokens.sessionId)
                put("tokenType", tokens.tokenType)
                put("accessToken", tokens.accessToken)
                put("accessTokenExpiresAtEpochMillis", tokens.accessTokenExpiresAtEpochMillis)
                put("refreshToken", tokens.refreshToken)
                put("refreshTokenExpiresAtEpochMillis", tokens.refreshTokenExpiresAtEpochMillis)
            })
        }
    }.toString()

    fun decode(encoded: String): OnlineAuthState {
        val body = json.parseToJsonElement(encoded).jsonObject
        require(body.requiredLong("schemaVersion") == 1L) {
            "Unsupported online auth state schema"
        }
        return OnlineAuthState(
            deviceId = body.requiredString("deviceId"),
            bootstrapIdempotencyKey = body.requiredString("bootstrapIdempotencyKey"),
            bootstrapAppVersionCode = body.optionalLong("bootstrapAppVersionCode"),
            tokens = body["tokens"]?.let { value ->
                if (value is JsonNull) null else value.jsonObject.toAuthTokens()
            },
            pendingRefreshIdempotencyKey = body.optionalString(
                "pendingRefreshIdempotencyKey"
            )
        )
    }
}

class OnlineAuthSessionManager(
    private val authApi: AuthApi,
    private val stateStore: OnlineAuthStateStore,
    private val appVersionCode: Long?,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val deviceIdFactory: () -> String = { "device_${UUID.randomUUID()}" },
    private val idempotencyKeyFactory: () -> String = { "auth_${UUID.randomUUID()}" },
    private val refreshLeewayMillis: Long = 60_000L
) : OnlineAuthTokenProvider {
    private val mutex = Mutex()

    init {
        require(appVersionCode == null || appVersionCode > 0) {
            "App version code must be positive"
        }
        require(refreshLeewayMillis >= 0) { "Refresh leeway must not be negative" }
    }

    override suspend fun token(): String? = mutex.withLock {
        val now = nowEpochMillis()
        var state = stateStore.read()
        if (state == null) {
            state = newInstallation()
            stateStore.write(state)
        }

        val storedTokens = state.tokens
        if (storedTokens == null) {
            return@withLock bootstrap(state, now)
        }
        if (!storedTokens.isValidForDevice(state.deviceId)) {
            return@withLock null
        }
        if (storedTokens.accessTokenExpiresAtEpochMillis - now > refreshLeewayMillis) {
            return@withLock storedTokens.accessToken
        }
        if (storedTokens.refreshTokenExpiresAtEpochMillis > now) {
            return@withLock refresh(state, storedTokens, now)
        }

        state = newInstallation()
        stateStore.write(state)
        bootstrap(state, now)
    }

    private suspend fun bootstrap(state: OnlineAuthState, now: Long): String? =
        when (val result = authApi.bootstrapAnonymous(
            AnonymousBootstrapRequest(
                deviceId = state.deviceId,
                idempotencyKey = state.bootstrapIdempotencyKey,
                appVersionCode = state.bootstrapAppVersionCode
            )
        )) {
            is OnlineResult.Success -> persistIssued(state, result.value, now)
            is OnlineResult.Failure -> null
        }

    private suspend fun refresh(
        initialState: OnlineAuthState,
        tokens: AuthTokens,
        now: Long
    ): String? {
        var state = initialState
        val idempotencyKey = state.pendingRefreshIdempotencyKey ?: newIdempotencyKey().also {
            state = state.copy(pendingRefreshIdempotencyKey = it)
            stateStore.write(state)
        }
        return when (val result = authApi.refreshAuth(
            RefreshAuthRequest(
                refreshToken = tokens.refreshToken,
                idempotencyKey = idempotencyKey
            )
        )) {
            is OnlineResult.Success -> persistIssued(state, result.value, now)
            is OnlineResult.Failure -> {
                if (result.userAction == "bootstrap_anonymous") {
                    val replacement = newInstallation()
                    stateStore.write(replacement)
                    bootstrap(replacement, now)
                } else {
                    null
                }
            }
        }
    }

    private suspend fun persistIssued(
        state: OnlineAuthState,
        issued: AuthTokens,
        now: Long
    ): String? {
        if (!issued.isUsableIssueFor(state.deviceId, now)) return null
        stateStore.write(
            state.copy(
                tokens = issued,
                pendingRefreshIdempotencyKey = null
            )
        )
        return issued.accessToken
    }

    private fun newInstallation(): OnlineAuthState {
        val deviceId = deviceIdFactory().trim()
        require(deviceId.length in 16..128) { "Generated device ID is invalid" }
        return OnlineAuthState(
            deviceId = deviceId,
            bootstrapIdempotencyKey = newIdempotencyKey(),
            bootstrapAppVersionCode = appVersionCode
        )
    }

    private fun newIdempotencyKey(): String = idempotencyKeyFactory().trim().also {
        require(it.isNotEmpty() && it.length <= 128) {
            "Generated auth idempotency key is invalid"
        }
    }
}

private fun AuthTokens.isValidForDevice(expectedDeviceId: String): Boolean =
    deviceId == expectedDeviceId &&
        tokenType == "Bearer" &&
        accountId.isNotBlank() &&
        sessionId.isNotBlank() &&
        accessToken.isNotBlank() &&
        refreshToken.isNotBlank()

private fun AuthTokens.isUsableIssueFor(expectedDeviceId: String, now: Long): Boolean =
    isValidForDevice(expectedDeviceId) &&
        accessTokenExpiresAtEpochMillis > now &&
        refreshTokenExpiresAtEpochMillis > now

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

private fun JsonObject.requiredString(name: String): String =
    get(name)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: error("Missing online auth state field: $name")

private fun JsonObject.requiredLong(name: String): Long =
    get(name)?.jsonPrimitive?.longOrNull
        ?: error("Missing online auth state field: $name")

private fun JsonObject.optionalString(name: String): String? =
    get(name)?.jsonPrimitive?.contentOrNull

private fun JsonObject.optionalLong(name: String): Long? =
    get(name)?.jsonPrimitive?.longOrNull

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableString(
    name: String,
    value: String?
) {
    if (value == null) put(name, JsonNull) else put(name, value)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableLong(
    name: String,
    value: Long?
) {
    if (value == null) put(name, JsonNull) else put(name, value)
}
