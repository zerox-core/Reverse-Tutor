package com.reversetutor.preview.wiring.session

import com.reversetutor.feature.chat.BackgroundTurnPreparationPort
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import com.reversetutor.feature.chat.TurnMonologuePort

/**
 * 反转家教内置预设 id：新建会话时写入 [com.reversetutor.feature.chat.NewSessionConfiguration.builtInPresetId]，
 * 路由端口据此把该会话的所有回合分派给算法内核协调器（ToolLoop 31 工具决策循环）。
 * 与 feature/chat FormalLearningPresets 的 id 命名空间互斥，不会冲突。
 */
const val REVERSE_TUTOR_PRESET_ID = "reverse-tutor"

/**
 * 会话级路由端口（装配层 R-RT-1）：
 * - 反转家教会话（builtInPresetId == [REVERSE_TUTOR_PRESET_ID]）→ [ReverseTutorTurnPreparationCoordinator]
 * - 其余会话 → 原有 RAG/图谱/记忆链路协调器
 *
 * 判定是确定性的（读请求自带快照，不做额外 IO），保持 BackgroundTurnPreparationPort
 * 的单一消费点（ChatScreen）零改动。
 *
 * 流式桥接（2026-10-11 拍板修复）：本端口同时实现 [TurnMonologuePort]。RT 协调器把
 * 流式预览/独白写进自己的 partialStore（与 DataModule 私有单例不同源），仓库直读读
 * 不到；这里把 reversePort 暴露的读口转发给 ChatScreen 观察循环。普通会话的 jobId
 * 不会出现在 RT store 里（返回 null），调用方回退仓库直读，行为不变。
 *
 * 连发合并（问题⑥ → 智能插队，2026-10-11 拍板升级）：普通会话每条消息入队前
 * 执行 [coalesceQueuedGeneration] 钩子——装配层闭包实现「仅当该会话最新活动生成
 * 任务仍是 Queued（未被 Worker 认领）时取消之」，随后全新的上下文装配自然包含
 * 此前未被回复的用户消息。RT 会话不走此钩子：其协调器同步 claim（Queued 窗口
 * 为零）且已升级为 ToolLoop 级智能插队——连发文本注入进行中的回合，模型带着
 * 新内容继续思考，合并为一条回复且顺序自然正确。Running 中的普通会话任务不受
 * 影响（不打断生成中）。
 */
class RoutingTurnPreparationPort(
    private val isReverseTutorSession: (BackgroundTurnPreparationRequest) -> Boolean =
        { request -> request.sessionSnapshot?.builtInPresetId == REVERSE_TUTOR_PRESET_ID },
    private val reversePort: BackgroundTurnPreparationPort,
    private val defaultPort: BackgroundTurnPreparationPort,
    private val coalesceQueuedGeneration: suspend (String) -> Unit = {}
) : BackgroundTurnPreparationPort, TurnMonologuePort {

    private val reverseMonologuePort = reversePort as? TurnMonologuePort

    override suspend fun prepareAndEnqueue(
        request: BackgroundTurnPreparationRequest
    ): BackgroundTurnPreparationResult {
        // 合并策略分流（2026-10-11 智能插队拍板）：RT 会话的连发由协调器内部
        // 吸收（activeJobs/pendingInjections 注入机制，不取消不新建），取消式
        // 合并钩子只对普通会话生效（其 job 由 Worker 异步认领，Queued 有真实
        // 窗口）；钩子异常不得阻断正常回合入队。
        return if (isReverseTutorSession(request)) {
            reversePort.prepareAndEnqueue(request)
        } else {
            runCatching { coalesceQueuedGeneration(request.sessionId) }
            defaultPort.prepareAndEnqueue(request)
        }
    }

    override fun getMonologue(jobId: String, token: String): String? =
        reverseMonologuePort?.getMonologue(jobId, token)

    override fun getPreview(jobId: String, token: String): String? =
        reverseMonologuePort?.getPreview(jobId, token)
}
