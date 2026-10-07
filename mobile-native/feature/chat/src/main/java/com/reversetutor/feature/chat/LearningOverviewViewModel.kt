package com.reversetutor.feature.chat

import com.reversetutor.core.domain.DailySummaryAiState
import com.reversetutor.core.domain.DailySummaryContract
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stable read-model port for the home learning overview side-panel. The UI never
 * touches a Repository or Coordinator directly — this port is the only entry and
 * is backed in production by [com.reversetutor.preview.wiring.session.SessionConversationAssembly.overview].
 */
interface LearningOverviewPort {
    suspend fun loadOverview(scope: LearningOverviewScope): LearningOverviewContract

    /**
     * V1 方向三「每日总结」懒生成触发（面板打开/刷新时调用）。
     * 默认空实现：测试替身与旧实现无需覆盖。
     */
    suspend fun requestDailySummaryGeneration(scope: LearningOverviewScope) {}

    /** 后台生成结束事件（成功或失败都会发一条），面板据此静默刷新。 */
    val dailySummaryUpdates: Flow<Unit>
        get() = emptyFlow()
}

/**
 * Pure UI projection of [LearningOverviewContract]. No mastery, mainline, or
 * weak-point derivation happens here — every field is a 1:1 projection of the
 * read model returned by the Coordinator. [dailySummaryAiStateLabel] 是 AI
 * 总结段状态的可读文案（生成中 / 生成于 HH:mm / 暂不可用）。
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
    val dailySummary: DailySummaryContract = DailySummaryContract(),
    val dailySummaryAiStateLabel: String = "",
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
        observeDailySummaryUpdates()
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

    /**
     * 静默监听每日总结生成结束事件：不打 loading，只更新数据——
     * 统计卡先上屏（打开即有），AI 总结段生成完成后自动补上。
     */
    private fun observeDailySummaryUpdates() {
        scope.launch {
            port.dailySummaryUpdates.collect {
                runCatching { port.loadOverview(mutableUiState.value.scope) }
                    .onSuccess { contract -> applyContract(contract) }
            }
        }
    }

    private fun refresh() {
        refreshJob?.cancel()
        val targetScope = mutableUiState.value.scope
        refreshJob = scope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            // V1 每日总结懒触发：面板打开时后台决定要不要生成（绝不在聊天轮次内同步跑）
            runCatching { port.requestDailySummaryGeneration(targetScope) }
            try {
                val contract = port.loadOverview(targetScope)
                applyContract(contract)
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

    private fun applyContract(contract: LearningOverviewContract) {
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
                dailySummary = contract.dailySummary,
                dailySummaryAiStateLabel = contract.dailySummary.toAiStateLabel(),
                warnings = contract.warnings.map { warning -> warning.toOverviewWarningMessage() },
                errorMessage = null
            )
        }
    }

    private fun DailySummaryContract.toAiStateLabel(): String = when (aiState) {
        DailySummaryAiState.Generating -> "AI 总结生成中…"
        DailySummaryAiState.Failed -> "AI 总结暂时不可用"
        DailySummaryAiState.Ready ->
            if (aiGeneratedAtEpochMillis > 0L) "生成于 " + toHHmm(aiGeneratedAtEpochMillis) else ""
        DailySummaryAiState.None -> ""
    }
}

private fun Long.toGeneratedAtLabel(): String =
    if (this <= 0L) "" else SimpleDateFormat("MM/dd HH:mm", Locale.SIMPLIFIED_CHINESE)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
        .format(Date(this))

private fun toHHmm(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
        .format(Date(epochMillis))

private fun Throwable.toOverviewErrorMessage(): String =
    "暂时无法加载学习概览，请稍后重试"

private fun ContextWarning.toOverviewWarningMessage(): String = when (source) {
    "session" -> "会话数据暂不可用"
    "progress" -> "学习进度暂不可用"
    "plan" -> "学习计划暂不可用"
    "mainline" -> "本周主线暂不可用"
    "weakPoints" -> "薄弱点数据暂不可用"
    "tokenUsage" -> "Token 用量暂不可用"
    "dailySummary" -> "每日总结暂不可用"
    else -> "部分学习数据暂不可用"
}
