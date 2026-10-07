package com.reversetutor.feature.chat

import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val WelcomeMockSessionId = "welcome-reverse-tutor"
const val WelcomeMockTitle = "欢迎来到反转家教"
const val WelcomeMockLearner = "小六子"
const val WelcomeMockOpening = "老师老师，第一节课我来教你，以后你就要好好来教我啦。"

internal const val SessionRenameMaxLength = 30
internal const val SessionDeleteUndoMillis = 5_000L

enum class SessionHomeSurfaceState {
    Loading,
    Content,
    Empty,
    Error
}

data class SessionDeleteUndo(
    val session: SessionListItem,
    val expiresAtEpochMillis: Long
)

data class SessionHomeUiState(
    val surfaceState: SessionHomeSurfaceState = SessionHomeSurfaceState.Loading,
    val sessions: List<SessionListItem> = emptyList(),
    val errorMessage: String? = null,
    val pendingDelete: SessionListItem? = null,
    val undo: SessionDeleteUndo? = null,
    val actionInProgress: Boolean = false
)

interface SessionHomePort {
    suspend fun loadSessionCards(): List<SessionListItem>
    suspend fun renameSession(sessionId: String, title: String, nowEpochMillis: Long): Boolean
    suspend fun setPinned(sessionId: String, pinned: Boolean, nowEpochMillis: Long): Boolean
    suspend fun setAvatarVisible(sessionId: String, visible: Boolean): Boolean = false
    suspend fun stageDelete(sessionId: String, nowEpochMillis: Long): Boolean
    suspend fun undoDelete(sessionId: String): Boolean
    suspend fun commitDelete(sessionId: String, nowEpochMillis: Long): Boolean

    suspend fun scheduleDeleteCommit(
        sessionId: String,
        delayMillis: Long,
        nowEpochMillis: Long
    ): Boolean {
        kotlinx.coroutines.delay(delayMillis)
        return commitDelete(sessionId, nowEpochMillis + delayMillis)
    }

    suspend fun cancelScheduledDelete(sessionId: String) = Unit
}

internal fun validateSessionTitle(title: String): String? {
    val normalized = title.trim()
    return when {
        normalized.isEmpty() -> "会话名称不能为空"
        normalized.length > SessionRenameMaxLength -> "会话名称最多 30 个字符"
        else -> null
    }
}

fun shouldCreateWelcomeSession(
    sessionExists: Boolean,
    deletionTombstoneExists: Boolean,
    hasOtherSessions: Boolean = false
): Boolean = !sessionExists && !deletionTombstoneExists && !hasOtherSessions

class SessionHomeViewModel(
    private val port: SessionHomePort,
    private val scope: CoroutineScope,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val undoWindowMillis: Long = SessionDeleteUndoMillis
) {
    private val mutableUiState = MutableStateFlow(SessionHomeUiState())
    val uiState: StateFlow<SessionHomeUiState> = mutableUiState.asStateFlow()
    private var refreshJob: Job? = null
    private var deleteJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            mutableUiState.update {
                it.copy(surfaceState = SessionHomeSurfaceState.Loading, errorMessage = null)
            }
            try {
                val sessions = port.loadSessionCards().sortedForHome()
                mutableUiState.update {
                    it.copy(
                        surfaceState = if (sessions.isEmpty()) {
                            SessionHomeSurfaceState.Empty
                        } else {
                            SessionHomeSurfaceState.Content
                        },
                        sessions = sessions,
                        errorMessage = null
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        surfaceState = SessionHomeSurfaceState.Error,
                        errorMessage = error.message?.takeIf(String::isNotBlank)
                            ?: "无法加载本地会话"
                    )
                }
            }
        }
    }

    fun requestDelete(sessionId: String) {
        val session = mutableUiState.value.sessions.firstOrNull { it.id == sessionId } ?: return
        mutableUiState.update { it.copy(pendingDelete = session) }
    }

    fun dismissDelete() {
        mutableUiState.update { it.copy(pendingDelete = null) }
    }

    fun rename(sessionId: String, title: String) {
        if (validateSessionTitle(title) != null) return
        val normalized = title.trim()
        scope.launch {
            mutableUiState.update { it.copy(actionInProgress = true, errorMessage = null) }
            try {
                check(port.renameSession(sessionId, normalized, nowEpochMillis())) {
                    "无法重命名会话"
                }
                mutableUiState.update { state ->
                    state.copy(
                        sessions = state.sessions.map { item ->
                            if (item.id == sessionId) item.copy(title = normalized) else item
                        }.sortedForHome(),
                        actionInProgress = false
                    )
                }
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(actionInProgress = false, errorMessage = error.message)
                }
            }
        }
    }

    fun togglePinned(sessionId: String) {
        val session = mutableUiState.value.sessions.firstOrNull { it.id == sessionId } ?: return
        val pinned = !session.pinned
        val now = nowEpochMillis()
        scope.launch {
            mutableUiState.update { it.copy(actionInProgress = true, errorMessage = null) }
            try {
                check(port.setPinned(sessionId, pinned, now)) { "无法更新置顶状态" }
                mutableUiState.update { state ->
                    state.copy(
                        sessions = state.sessions.map { item ->
                            if (item.id == sessionId) {
                                item.copy(
                                    pinned = pinned,
                                    pinnedAtEpochMillis = now.takeIf { pinned }
                                )
                            } else {
                                item
                            }
                        }.sortedForHome(),
                        actionInProgress = false
                    )
                }
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(actionInProgress = false, errorMessage = error.message)
                }
            }
        }
    }

    fun setAvatarVisible(sessionId: String, visible: Boolean) {
        if (mutableUiState.value.sessions.none { it.id == sessionId }) return
        scope.launch {
            mutableUiState.update { it.copy(actionInProgress = true, errorMessage = null) }
            try {
                check(port.setAvatarVisible(sessionId, visible)) {
                    "无法更新会话头像显示"
                }
                mutableUiState.update { state ->
                    state.copy(
                        sessions = state.sessions.map { item ->
                            if (item.id == sessionId) {
                                item.copy(perSessionAvatarVisible = visible)
                            } else {
                                item
                            }
                        },
                        actionInProgress = false
                    )
                }
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(actionInProgress = false, errorMessage = error.message)
                }
            }
        }
    }

    fun confirmDelete() {
        val session = mutableUiState.value.pendingDelete ?: return
        val previousUndo = mutableUiState.value.undo
        deleteJob?.cancel()
        val stagedAt = nowEpochMillis()
        scope.launch {
            mutableUiState.update { it.copy(actionInProgress = true, errorMessage = null) }
            try {
                if (previousUndo != null) {
                    check(port.commitDelete(previousUndo.session.id, stagedAt)) {
                        "无法完成上一项删除"
                    }
                }
                check(port.stageDelete(session.id, stagedAt)) { "无法删除会话" }
                mutableUiState.update { state ->
                    val remaining = state.sessions.filterNot { it.id == session.id }
                    state.copy(
                        surfaceState = if (remaining.isEmpty()) {
                            SessionHomeSurfaceState.Empty
                        } else {
                            SessionHomeSurfaceState.Content
                        },
                        sessions = remaining,
                        pendingDelete = null,
                        undo = SessionDeleteUndo(session, stagedAt + undoWindowMillis),
                        actionInProgress = false
                    )
                }
                deleteJob = scope.launch {
                    port.scheduleDeleteCommit(session.id, undoWindowMillis, stagedAt)
                    mutableUiState.update { state ->
                        if (state.undo?.session?.id == session.id) state.copy(undo = null) else state
                    }
                }
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        pendingDelete = null,
                        actionInProgress = false,
                        errorMessage = error.message
                    )
                }
            }
        }
    }

    fun undoDelete() {
        val undo = mutableUiState.value.undo ?: return
        deleteJob?.cancel()
        deleteJob = null
        scope.launch {
            try {
                port.cancelScheduledDelete(undo.session.id)
                check(port.undoDelete(undo.session.id)) { "无法恢复会话" }
                mutableUiState.update { state ->
                    val restored = (state.sessions + undo.session).distinctBy { it.id }.sortedForHome()
                    state.copy(
                        surfaceState = SessionHomeSurfaceState.Content,
                        sessions = restored,
                        undo = null,
                        errorMessage = null
                    )
                }
            } catch (error: Throwable) {
                mutableUiState.update { it.copy(errorMessage = error.message) }
            }
        }
    }
}

internal fun List<SessionListItem>.sortedForHome(): List<SessionListItem> =
    sortedWith(
        compareByDescending<SessionListItem> { it.pinned }
            .thenByDescending { it.pinnedAtEpochMillis ?: Long.MIN_VALUE }
            .thenByDescending { it.updatedAtEpochMillis }
    )
