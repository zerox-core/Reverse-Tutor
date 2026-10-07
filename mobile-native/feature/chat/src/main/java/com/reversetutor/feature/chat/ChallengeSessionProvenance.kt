package com.reversetutor.feature.chat

import java.util.Locale

data class ChallengeSessionProvenance(
    val activityId: String
) {
    val canonicalSource: String
        get() = canonicalActivitySource(activityId)
}

fun canonicalActivitySource(activityId: String): String =
    "activity:${activityId.trim().lowercase(Locale.ROOT)}"

fun normalizeActivitySource(value: String): String? {
    val separator = value.indexOf(':')
    if (separator < 0 || !value.substring(0, separator).trim().equals("activity", true)) {
        return null
    }
    val id = value.substring(separator + 1).trim().lowercase(Locale.ROOT)
    return id.takeIf(String::isNotEmpty)?.let(::canonicalActivitySource)
}

fun NewSessionConfiguration.challengeSessionProvenance(): ChallengeSessionProvenance? =
    sourceSelections.asSequence()
        .mapNotNull(::normalizeActivitySource)
        .firstOrNull()
        ?.substringAfter("activity:")
        ?.let(::ChallengeSessionProvenance)
