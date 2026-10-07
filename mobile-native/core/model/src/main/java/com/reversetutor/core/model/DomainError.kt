package com.reversetutor.core.model

data class DomainError(
    val code: DomainErrorCode,
    val retryable: Boolean,
    val safeMessage: String,
    val userAction: DomainUserAction = if (retryable) {
        DomainUserAction.Retry
    } else {
        DomainUserAction.None
    }
)

enum class DomainErrorCode {
    Unknown,
    Offline,
    Timeout,
    InvalidRequest,
    InvalidUrl,
    InvalidCredential,
    PermissionDenied,
    ModelMissing,
    QuotaExceeded,
    RateLimited,
    ProviderUnavailable,
    ProtocolError,
    StorageUnavailable,
    Conflict,
    NotFound,
    Cancelled,
    Deleted,
    StaleAttempt
}

enum class DomainUserAction {
    None,
    Retry,
    CheckConnection,
    UpdateCredential,
    SelectModel,
    ResolveConflict
}
