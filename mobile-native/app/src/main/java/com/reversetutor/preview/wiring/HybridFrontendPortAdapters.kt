package com.reversetutor.preview.wiring

import com.reversetutor.core.domain.ConversationRunCoordinator
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.run.ConversationRunRepositoryImpl
import com.reversetutor.core.data.session.SessionCreationInput
import com.reversetutor.core.data.session.SessionDeletionRepository
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageRole
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ProviderConnection
import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.TurnRun
import com.reversetutor.core.model.TurnRunState
import com.reversetutor.core.model.TutorSession
import com.reversetutor.core.model.WeeklySummary
import com.reversetutor.feature.chat.ChatRunsPort
import com.reversetutor.feature.chat.HomePort
import com.reversetutor.feature.chat.SessionHomePort
import com.reversetutor.feature.chat.SessionListItem
import com.reversetutor.feature.chat.WelcomeMockLearner
import com.reversetutor.feature.chat.WelcomeMockOpening
import com.reversetutor.feature.chat.WelcomeMockSessionId
import com.reversetutor.feature.chat.WelcomeMockTitle
import com.reversetutor.feature.chat.shouldCreateWelcomeSession
import com.reversetutor.feature.memory.WeeklyDashboardPort
import com.reversetutor.feature.memory.WeeklyDashboardSnapshot
import com.reversetutor.feature.settings.ModelConnectionsPort
import com.reversetutor.feature.settings.ModelConnectionsSnapshot

class RepositoryHomePortAdapter(
    private val listSessions: suspend () -> List<TutorSession>
) : HomePort {
    override suspend fun loadLocalSessions(): List<TutorSession> =
        listSessions()
}

class RepositorySessionHomePortAdapter(
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val sessionDeletionRepository: SessionDeletionRepository,
    private val conversationRunRepository: ConversationRunRepositoryImpl,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : SessionHomePort {
    private val stagedSessions = mutableMapOf<String, TutorSession>()

    override suspend fun loadSessionCards(): List<SessionListItem> {
        ensureWelcomeSession()
        return sessionRepository.listSessions()
            .filterNot { it.archived }
            .map { session ->
                val messages = messageRepository.listMessages(session.id)
                val settings = sessionRepository.getSessionSettings(session.id)
                val learnerRole = settings?.systemPrompt
                    ?.lineSequence()
                    ?.firstOrNull { it.startsWith("Role:") }
                    ?.substringAfter("Role:")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: if (session.id == WelcomeMockSessionId) WelcomeMockLearner else "学习者"
                val latestSummary = messages.lastOrNull()?.text
                    ?.trim()
                    ?.replace(Regex("\\s+"), " ")
                    ?.take(80)
                    ?.takeIf { it.isNotEmpty() }
                    ?: "开始一段新的反向教学"
                val latestMessageAt = messages.lastOrNull()?.createdAtEpochMillis
                    ?: session.updatedAtEpochMillis
                SessionListItem(
                    id = session.id,
                    title = session.title,
                    updatedAtEpochMillis = latestMessageAt,
                    pinned = session.pinned,
                    statusLabel = latestSummary,
                    unreadCount = 0,
                    avatarLabel = session.title.trim().take(1),
                    learnerRole = learnerRole,
                    latestMessageSummary = latestSummary,
                    perSessionAvatarVisible = true,
                    pinnedAtEpochMillis = session.updatedAtEpochMillis.takeIf { session.pinned },
                    isWelcomeMock = session.id == WelcomeMockSessionId
                )
            }
    }

    override suspend fun renameSession(
        sessionId: String,
        title: String,
        nowEpochMillis: Long
    ): Boolean {
        val existing = sessionRepository.getSession(sessionId) ?: return false
        if (!sessionRepository.renameSession(sessionId, title, nowEpochMillis)) return false
        sessionRepository.saveSession(existing.copy(title = title.trim()))
        return true
    }

    override suspend fun setPinned(
        sessionId: String,
        pinned: Boolean,
        nowEpochMillis: Long
    ): Boolean = sessionRepository.setPinned(sessionId, pinned, nowEpochMillis)

    override suspend fun stageDelete(sessionId: String, nowEpochMillis: Long): Boolean {
        val session = sessionRepository.getSession(sessionId) ?: return false
        stagedSessions[sessionId] = session
        return sessionRepository.archiveSession(sessionId, nowEpochMillis)
    }

    override suspend fun undoDelete(sessionId: String): Boolean {
        val session = stagedSessions.remove(sessionId) ?: return false
        sessionRepository.saveSession(session.copy(archived = false))
        return true
    }

    override suspend fun commitDelete(sessionId: String, nowEpochMillis: Long): Boolean {
        stagedSessions.remove(sessionId)
        return sessionDeletionRepository.deleteSession(
            sessionId = sessionId,
            deletedAtEpochMillis = nowEpochMillis,
            revision = nowEpochMillis,
            idempotencyKey = "session-home-delete-$sessionId-$nowEpochMillis"
        )
    }

    private suspend fun ensureWelcomeSession() {
        val existing = sessionRepository.getSession(WelcomeMockSessionId)
        if (existing == null) {
            val tombstoneExists = conversationRunRepository.isSessionDeleted(WelcomeMockSessionId)
            val hasOtherSessions = sessionRepository.listSessions().any { !it.archived }
            if (!shouldCreateWelcomeSession(
                    sessionExists = false,
                    deletionTombstoneExists = tombstoneExists,
                    hasOtherSessions = hasOtherSessions
                )
            ) return
            sessionRepository.createSession(
                input = SessionCreationInput(
                    title = WelcomeMockTitle,
                    role = WelcomeMockLearner,
                    goal = "通过反向教学完成第一次本地学习会话",
                    profileText = "活泼、主动提问的六年级学习者",
                    templateId = "first-use-mock"
                ),
                nowEpochMillis = nowEpochMillis(),
                sessionId = WelcomeMockSessionId
            )
        } else if (existing.archived) {
            return
        }

        if (messageRepository.listMessages(WelcomeMockSessionId).isEmpty()) {
            val createdAt = nowEpochMillis()
            messageRepository.saveMessage(
                Message(
                    id = "message-$WelcomeMockSessionId-opening",
                    spaceId = SessionRepository.defaultSpaceId,
                    sessionId = WelcomeMockSessionId,
                    role = MessageRole.Assistant,
                    text = WelcomeMockOpening,
                    createdAtEpochMillis = createdAt
                )
            )
        }
    }
}

class RepositoryChatRunsPortAdapter(
    private val listRuns: suspend (String) -> List<TurnRun>,
    private val findRun: suspend (String) -> TurnRun?,
    private val saveRun: suspend (TurnRun) -> TurnRun,
    private val isWritableAttempt: suspend (String) -> Boolean,
    private val runCoordinator: ConversationRunCoordinator,
    private val setModelBinding: suspend (String, String) -> Boolean,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : ChatRunsPort {
    override suspend fun loadRuns(sessionId: String): List<TurnRun> =
        listRuns(sessionId)

    override suspend fun stopRun(runId: String): List<TurnRun> {
        val run = requireNotNull(findRun(runId)) { "TurnRun not found: $runId" }
        if (!run.isTerminal) {
            check(isWritableAttempt(runId)) {
                "TurnRun is no longer writable: $runId"
            }
            saveRun(
                run.copy(
                    state = TurnRunState.Cancelled,
                    completedAtEpochMillis = nowEpochMillis()
                )
            )
        }
        return listRuns(run.sessionId)
    }

    override suspend fun retryRun(runId: String): List<TurnRun> {
        val run = requireNotNull(findRun(runId)) { "TurnRun not found: $runId" }
        runCoordinator.retryRun(runId)
        return listRuns(run.sessionId)
    }

    override suspend fun switchSessionModel(sessionId: String, modelBindingId: String) {
        check(setModelBinding(sessionId, modelBindingId)) {
            "Unable to update model binding for session: $sessionId"
        }
    }
}

class RepositoryModelConnectionsPortAdapter(
    private val listConnections: suspend () -> List<ProviderConnection>,
    private val listBindings: suspend () -> List<ModelBinding>,
    private val setModelBinding: suspend (String, String) -> Boolean
) : ModelConnectionsPort {
    override suspend fun loadSnapshot(): ModelConnectionsSnapshot =
        ModelConnectionsSnapshot(
            connections = listConnections(),
            bindings = listBindings()
        )

    override suspend fun selectSessionModel(sessionId: String, modelBindingId: String) {
        check(setModelBinding(sessionId, modelBindingId)) {
            "Unable to update model binding for session: $sessionId"
        }
    }
}

class RepositoryWeeklyDashboardPortAdapter(
    private val listSummaries: suspend () -> List<WeeklySummary>,
    private val listTasks: suspend () -> List<StudyPlanTask>
) : WeeklyDashboardPort {
    override suspend fun loadLocalSnapshot(): WeeklyDashboardSnapshot =
        WeeklyDashboardSnapshot(
            summary = listSummaries().maxWithOrNull(
                compareBy<WeeklySummary> { it.weekStartEpochMillis }
                    .thenBy { it.generatedAtEpochMillis }
            ),
            tasks = listTasks()
        )
}
