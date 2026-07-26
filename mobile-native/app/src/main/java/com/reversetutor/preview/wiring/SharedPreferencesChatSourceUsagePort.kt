package com.reversetutor.preview.wiring

import com.reversetutor.feature.chat.ChatSourceUsagePort
import com.reversetutor.feature.chat.SessionSettingsStore

class SharedPreferencesChatSourceUsagePort(
    private val store: SessionSettingsStore
) : ChatSourceUsagePort {
    override fun recordSourcesUsed(sessionId: String, sourceIds: Set<String>, usedAtEpochMillis: Long) {
        store.recordSourcesUsed(sessionId, sourceIds, usedAtEpochMillis)
    }
}
