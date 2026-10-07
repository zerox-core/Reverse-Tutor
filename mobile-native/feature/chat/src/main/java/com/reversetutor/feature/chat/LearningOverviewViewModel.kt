package com.reversetutor.feature.chat

import com.reversetutor.core.domain.LearningOverviewContract
import com.reversetutor.core.domain.LearningOverviewScope
import com.reversetutor.core.domain.LearningProgressContract
import com.reversetutor.core.domain.LearningThreadContract
import com.reversetutor.core.domain.ContextWarning
import com.reversetutor.core.domain.TodayPlanContract
import com.reversetutor.core.domain.TokenUsageOverviewContract
import com.reversetutor.core.domain.WeakPointContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stable read-model port for the home learning overview side-panel. The UI never
 * touches a Repository or Coordinator directly — this port is the only entry and
 * is backed in production by [com.reversetutor.preview.wiring.session.SessionConversationAssembly.overview].
 */
interface LearningOverviewPort {
    suspend fun loadOverview(scope: LearningOverviewScope): LearningOverviewContract
}

/**
 * Pure UI projection of [LearningOverviewContract]. No mastery, mainline, or
 * weak-point derivation happens here — every field is a 1:1 projection of the
 * read model returned by the Coordinator.
 */
data class LearningOverviewUiState(
    val scope: LearningOverviewScope,
    val isLoading: Boolean = false,
    val isNoData: Boolean = false,
    val generatedAtLabel: String = "",
    val activeSessionCount: Int = 0,
    val progress: LearningProgressContract = LearningProgressContract(),
    val todayPlan: TodayPlanContract = TodayPlanContract(),
    val weeklyMainline: List<LearningThreadContract> = emptyList(),
    val weakPoints: List<WeakPointContract> = emptyList(),
    val tokenUsage: TokenUsageOverviewContract = TokenUsageOverviewContract(),
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null
) {
    val masteryPercent: Int
        get() = (progress.masteryRate * 100).toInt().coerceIn(0, 100)
    val weeklyChangePercent: Int
        get() = (progress.weeklyChange * 100).toInt()
}

sealed interface LearningOverviewUiAction {
    object Refresh : LearningOverviewUiAction
    data class ChangeScope(val scope: LearningOverviewScope) : LearningOverviewUiAction
}

fun interface LearningOverviewViewModelFactory {
    fun create(scope: LearningOverviewScope, coroutineScope: CoroutineScope): LearningOverviewViewModel
}

class LearningOverviewPortViewModelFactory(
    private val port: LearningOverviewPort
) : LearningOverviewViewModelFactory {
    override fun create(
        scope: LearningOverviewScope,
        coroutineScope: CoroutineScope
    ): LearningOverviewViewModel =
        LearningOverviewViewModel(port = port, initialScope = scope, scope = coroutineScope)
}

class LearningOverviewViewModel(
    private val port: LearningOverviewPort,
    initialScope: LearningOverviewScope,
    private val scope: CoroutineScope
) {
    private val mutableUiState = MutableStateFlow(LearningOverviewUiState(scope = initialScope))
    val uiState: StateFlow<LearningOverviewUiState> = mutableUiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun onAction(action: LearningOverviewUiAction) {
        when (action) {
            LearningOverviewUiAction.Refresh -> refresh()
            is LearningOverviewUiAction.ChangeScope -> {
                mutableUiState.update { it.copy(scope = action.scope) }
                refresh()
            }
        }
    }

    private fun refresh() {
        refreshJob?.cancel()
        val targetScope = mutableUiState.value.scope
        refreshJob = scope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val contract = port.loadOverview(targetScope)
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        isNoData = contract.isNoData,
                        generatedAtLabel = contract.generatedAtEpochMillis.toGeneratedAtLabel(),
                        activeSessionCount = contract.activeSessionCount,
                        progress = contract.progress,
                        todayPlan = contract.todayPlan,
                        weeklyMainline = contract.weeklyMainline,
                        weakPoints = contract.weakPoints,
                        tokenUsage = contract.tokenUsage,
                        warnings = contract.warnings.map { warning -> warning.toOverviewWarningMessage() },
                        errorMessage = null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.toOverviewErrorMessage()
                    )
                }
            }
        }
    }
}

private fun Long.toGeneratedAtLabel(): String =
    if (this <= 0L) "" else SimpleDateFormat("MM/dd HH:mm", Locale.SIMPLIFIED_CHINESE)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
        .format(Date(this))

private fun Throwable.toOverviewErrorMessage(): String =
    "暂时无法加载学习概览，请稍后重试"

private fun ContextWarning.toOverviewWarningMessage(): String = when (source) {
    "session" -> "会话数据暂不可用"
    "progress" -> "学习进度暂不可用"
    "plan" -> "学习计划暂不可用"
    "mainline" -> "本周主线暂不可用"
    "weakPoints" -> "薄弱点数据暂不可用"
    "tokenUsage" -> "Token 用量暂不可用"
    else -> "部分学习数据暂不可用"
}
