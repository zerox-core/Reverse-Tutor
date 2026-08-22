package com.reversetutor.preview.wiring

import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.run.ConversationRunRepositoryImpl
import com.reversetutor.core.data.session.SessionCreationInput
import com.reversetutor.core.data.session.SessionDeletionRepository
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.domain.ConversationRunCoordinator
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
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.HomePort
import com.reversetutor.feature.chat.SessionHomePort
import com.reversetutor.feature.chat.SessionHomePersistence
import com.reversetutor.feature.chat.SessionListItem
import com.reversetutor.feature.chat.WelcomeMockLearner
import com.reversetutor.feature.chat.WelcomeMockOpening
import com.reversetutor.feature.chat.WelcomeMockSessionId
import com.reversetutor.feature.chat.WelcomeMockTitle
import com.reversetutor.feature.chat.shouldCreateWelcomeSession
import com.reversetutor.feature.chat.challengeSessionProvenance
import com.reversetutor.feature.chat.LearningOverviewPort
import com.reversetutor.core.domain.LearningOverviewContract
import com.reversetutor.core.domain.LearningOverviewScope
import com.reversetutor.preview.wiring.session.SessionConversationAssembly
import com.reversetutor.feature.memory.WeeklyDashboardPort
import com.reversetutor.feature.memory.WeeklyDashboardSnapshot
import com.reversetutor.feature.memory.WeeklyTokenUsageEntry
import com.reversetutor.feature.settings.ModelConnectionsPort
import com.reversetutor.feature.settings.ModelConnectionsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    private val persistence: SessionHomePersistence,
    private val loadSessionSnapshot: (String) -> NewSessionConfiguration? = { null },
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val deletionScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : SessionHomePort {
    private val deletionCoordinator = DurableSessionDeletionCoordinator(
        persistence = persistence,
        sessionExists = { sessionId -> sessionRepository.getSession(sessionId) != null },
        deleteSession = { sessionId, deletedAtEpochMillis, revision, idempotencyKey ->
            sessionDeletionRepository.deleteSession(
                sessionId = sessionId,
                deletedAtEpochMillis = deletedAtEpochMillis,
                revision = revision,
                idempotencyKey = idempotencyKey
            )
        },
        completeConfirmedDeletion = { sessionId ->
            persistence.completeConfirmedDeletion(
                sessionId = sessionId,
                suppressWelcome = sessionId == WelcomeMockSessionId
            )
        },
        nowEpochMillis = nowEpochMillis,
        scope = deletionScope
    )

    override suspend fun loadSessionCards(): List<SessionListItem> {
        recoverPendingDeletes()
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
                    perSessionAvatarVisible = persistence.avatarVisible(session.id) ?: true,
                    pinnedAtEpochMillis = if (session.pinned) {
                        persistence.pinnedAt(session.id) ?: session.updatedAtEpochMillis
                    } else {
                        null
                    },
                    isWelcomeMock = session.id == WelcomeMockSessionId,
                    challengeProvenance = loadSessionSnapshot(session.id)
                        ?.challengeSessionProvenance()
                )
            }
    }

    override suspend fun renameSession(
        sessionId: String,
        title: String,
        nowEpochMillis: Long
    ): Boolean {
        return sessionRepository.renameSession(sessionId, title.trim(), nowEpochMillis)
    }

    override suspend fun setPinned(
        sessionId: String,
        pinned: Boolean,
        nowEpochMillis: Long
    ): Boolean {
        val updated = sessionRepository.setPinned(sessionId, pinned, nowEpochMillis)
        if (updated) persistence.setPinnedAt(sessionId, nowEpochMillis.takeIf { pinned })
        return updated
    }

    override suspend fun setAvatarVisible(sessionId: String, visible: Boolean): Boolean {
        sessionRepository.getSession(sessionId) ?: return false
        persistence.setAvatarVisible(sessionId, visible)
        return true
    }

    override suspend fun stageDelete(sessionId: String, nowEpochMillis: Long): Boolean {
        sessionRepository.getSession(sessionId) ?: return false
        val archived = sessionRepository.archiveSession(sessionId, nowEpochMillis)
        if (archived) {
            persistence.setPendingDeleteAt(sessionId, nowEpochMillis + 5_000L)
        }
        return archived
    }

    override suspend fun undoDelete(sessionId: String): Boolean {
        deletionCoordinator.cancelAndJoin(sessionId)
        val session = sessionRepository.getSession(sessionId) ?: return false
        if (persistence.pendingDeleteAt(sessionId) == null) return false
        persistence.clearPendingDelete(sessionId)
        sessionRepository.saveSession(session.copy(archived = false))
        return true
    }

    override suspend fun commitDelete(sessionId: String, nowEpochMillis: Long): Boolean =
        deletionCoordinator.finalizeDirect(sessionId, nowEpochMillis)

    override suspend fun scheduleDeleteCommit(
        sessionId: String,
        delayMillis: Long,
        nowEpochMillis: Long
    ): Boolean {
        val dueAt = persistence.pendingDeleteAt(sessionId)
            ?: (nowEpochMillis + delayMillis).also {
                persistence.setPendingDeleteAt(sessionId, it)
            }
        return deletionCoordinator.schedule(sessionId, dueAt)
    }

    override suspend fun cancelScheduledDelete(sessionId: String) {
        deletionCoordinator.cancelAndJoin(sessionId)
    }

    private suspend fun recoverPendingDeletes() {
        val now = nowEpochMillis()
        persistence.pendingDeletes().forEach { (sessionId, dueAt) ->
            if (dueAt <= now) {
                deletionCoordinator.finalizeDirect(sessionId, now)
            } else {
                deletionCoordinator.startRecovery(sessionId, dueAt)
            }
        }
    }

    private suspend fun ensureWelcomeSession() {
        val existing = sessionRepository.getSession(WelcomeMockSessionId)
        if (existing == null) {
            val tombstoneExists = persistence.isWelcomeDeletionSuppressed() ||
                (persistence.hasObservedWelcomeSession() &&
                    conversationRunRepository.isSessionDeleted(WelcomeMockSessionId))
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
            persistence.setWelcomeSessionObserved()
        } else if (existing.archived) {
            persistence.setWelcomeSessionObserved()
            return
        }

        persistence.setWelcomeSessionObserved()

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
    private val listTasks: suspend () -> List<StudyPlanTask>,
    private val listTokenUsage: suspend () -> List<WeeklyTokenUsageEntry> = { emptyList() },
    private val saveTask: suspend (StudyPlanTask) -> StudyPlanTask = { it }
) : WeeklyDashboardPort {
    override suspend fun loadLocalSnapshot(): WeeklyDashboardSnapshot =
        WeeklyDashboardSnapshot(
            summary = listSummaries().maxWithOrNull(
                compareBy<WeeklySummary> { it.weekStartEpochMillis }
                    .thenBy { it.generatedAtEpochMillis }
            ),
            tasks = listTasks(),
            tokenUsage = listTokenUsage()
        )

    override suspend fun saveTask(task: StudyPlanTask): StudyPlanTask = saveTask.invoke(task)
}

class RepositoryLearningOverviewPortAdapter(
    private val assembly: SessionConversationAssembly
) : LearningOverviewPort {
    override suspend fun loadOverview(scope: LearningOverviewScope): LearningOverviewContract =
        assembly.overview(scope)
}
