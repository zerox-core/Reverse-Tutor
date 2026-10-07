package com.reversetutor.feature.chat

interface SessionHomePersistence {
    fun hasObservedWelcomeSession(): Boolean
    fun setWelcomeSessionObserved()
    fun isWelcomeDeletionSuppressed(): Boolean
    fun setWelcomeDeletionSuppressed(suppressed: Boolean)

    fun avatarVisible(sessionId: String): Boolean?
    fun setAvatarVisible(sessionId: String, visible: Boolean)

    fun pinnedAt(sessionId: String): Long?
    fun setPinnedAt(sessionId: String, pinnedAtEpochMillis: Long?)
    fun completeConfirmedDeletion(sessionId: String, suppressWelcome: Boolean)

    fun pendingDeleteAt(sessionId: String): Long?
    fun pendingDeletes(): Map<String, Long>
    fun setPendingDeleteAt(sessionId: String, dueAtEpochMillis: Long)
    fun clearPendingDelete(sessionId: String)
}

class InMemorySessionHomePersistence : SessionHomePersistence {
    private val avatarVisibility = mutableMapOf<String, Boolean>()
    private val pendingDeletesBySession = mutableMapOf<String, Long>()
    private val pinnedAtBySession = mutableMapOf<String, Long>()
    private var welcomeObserved = false
    private var welcomeDeletionSuppressed = false

    override fun hasObservedWelcomeSession(): Boolean = welcomeObserved

    override fun setWelcomeSessionObserved() {
        welcomeObserved = true
    }

    override fun isWelcomeDeletionSuppressed(): Boolean = welcomeDeletionSuppressed

    override fun setWelcomeDeletionSuppressed(suppressed: Boolean) {
        welcomeDeletionSuppressed = suppressed
    }

    override fun avatarVisible(sessionId: String): Boolean? = avatarVisibility[sessionId]

    override fun setAvatarVisible(sessionId: String, visible: Boolean) {
        avatarVisibility[sessionId] = visible
    }

    override fun pinnedAt(sessionId: String): Long? = pinnedAtBySession[sessionId]

    override fun setPinnedAt(sessionId: String, pinnedAtEpochMillis: Long?) {
        if (pinnedAtEpochMillis == null) {
            pinnedAtBySession.remove(sessionId)
        } else {
            pinnedAtBySession[sessionId] = pinnedAtEpochMillis
        }
    }

    override fun completeConfirmedDeletion(sessionId: String, suppressWelcome: Boolean) {
        if (suppressWelcome) welcomeDeletionSuppressed = true
        avatarVisibility.remove(sessionId)
        pinnedAtBySession.remove(sessionId)
        pendingDeletesBySession.remove(sessionId)
    }

    override fun pendingDeleteAt(sessionId: String): Long? = pendingDeletesBySession[sessionId]

    override fun pendingDeletes(): Map<String, Long> = pendingDeletesBySession.toMap()

    override fun setPendingDeleteAt(sessionId: String, dueAtEpochMillis: Long) {
        pendingDeletesBySession[sessionId] = dueAtEpochMillis
    }

    override fun clearPendingDelete(sessionId: String) {
        pendingDeletesBySession.remove(sessionId)
    }
}
