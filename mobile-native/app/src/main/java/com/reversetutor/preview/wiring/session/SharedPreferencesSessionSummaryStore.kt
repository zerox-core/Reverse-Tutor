package com.reversetutor.preview.wiring.session

import android.content.Context
import android.content.SharedPreferences

/**
 * NEWMP-V1-017: SharedPreferences-backed [SessionSummaryStore]. Implements
 * the domain digest read through [SessionSummaryStore]'s
 * [com.reversetutor.core.domain.SessionDigestContextPort] supertype, so the
 * context assembler can consume the stored digest without knowing the
 * storage details.
 */
class SharedPreferencesSessionSummaryStore(context: Context) : SessionSummaryStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun load(sessionId: String): SessionSummaryRecord? {
        val text = prefs.getString(textKey(sessionId), null)?.takeIf { it.isNotBlank() } ?: return null
        val untilMessageId = prefs.getString(untilKey(sessionId), null) ?: return null
        return SessionSummaryRecord(
            summaryText = text,
            summarizedUntilMessageId = untilMessageId,
            summarizedCount = prefs.getInt(countKey(sessionId), 0)
        )
    }

    override fun save(sessionId: String, record: SessionSummaryRecord) {
        prefs.edit()
            .putString(textKey(sessionId), record.summaryText)
            .putString(untilKey(sessionId), record.summarizedUntilMessageId)
            .putInt(countKey(sessionId), record.summarizedCount)
            .apply()
    }

    override fun clear(sessionId: String) {
        prefs.edit()
            .remove(textKey(sessionId))
            .remove(untilKey(sessionId))
            .remove(countKey(sessionId))
            .apply()
    }

    override suspend fun loadEarlyHistoryDigest(spaceId: String, sessionId: String): String =
        load(sessionId)?.summaryText.orEmpty()

    private companion object {
        const val PrefsName = "session_summaries"
        fun textKey(sessionId: String): String = "text-" + sessionId
        fun untilKey(sessionId: String): String = "until-" + sessionId
        fun countKey(sessionId: String): String = "count-" + sessionId
    }
}
