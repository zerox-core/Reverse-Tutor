package com.reversetutor.core.remote

data class AnonymousBootstrapRequest(
    val deviceId: String,
    val idempotencyKey: String,
    val appVersionCode: Long? = null
)

data class RefreshAuthRequest(
    val refreshToken: String,
    val idempotencyKey: String
)

data class AuthTokens(
    val accountId: String,
    val deviceId: String,
    val sessionId: String,
    val tokenType: String,
    val accessToken: String,
    val accessTokenExpiresAtEpochMillis: Long,
    val refreshToken: String,
    val refreshTokenExpiresAtEpochMillis: Long
)

data class AuthIdentity(
    val accountId: String,
    val deviceId: String,
    val sessionId: String,
    val accountType: String,
    val sessionExpiresAtEpochMillis: Long
)

data class AuthSession(
    val sessionId: String,
    val deviceId: String,
    val status: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val current: Boolean
)

data class AuthSessionPage(
    val items: List<AuthSession>
)
