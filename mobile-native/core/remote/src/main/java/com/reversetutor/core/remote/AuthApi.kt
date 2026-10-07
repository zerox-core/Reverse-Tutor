package com.reversetutor.core.remote

interface AuthApi {
    suspend fun bootstrapAnonymous(
        request: AnonymousBootstrapRequest
    ): OnlineResult<AuthTokens>

    suspend fun refreshAuth(request: RefreshAuthRequest): OnlineResult<AuthTokens>

    suspend fun authMe(): OnlineResult<AuthIdentity>

    suspend fun authSessions(): OnlineResult<AuthSessionPage>

    suspend fun revokeAuthSession(sessionId: String): OnlineResult<Unit>
}
