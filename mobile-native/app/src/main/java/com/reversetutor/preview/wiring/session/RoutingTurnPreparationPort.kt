package com.reversetutor.preview.wiring.session

import com.reversetutor.feature.chat.BackgroundTurnPreparationPort
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import com.reversetutor.feature.chat.FormalLearningPresets
import com.reversetutor.feature.chat.TurnMonologuePort

/**
 * 「反转家教课堂」内置预设卡的 id（装配层别名，真源在 `FormalLearningPresets.REVERSE_TUTOR_PRESET_ID`）。
 *
 * 2026-10-18 起它**不再是路由键**（路由改为「应用内创建的会话（有落盘快照）一律进内核」，
 * 见 [RoutingTurnPreparationPort]），保留为装配层/测试对内置预设 id 的单源引用，避免字面量漂移。
 */
const val REVERSE_TUTOR_PRESET_ID: String = FormalLearningPresets.REVERSE_TUTOR_PRESET_ID

/**
 * 会话级路由端口（装配层 R-RT-1）：
 * - **应用内创建的会话**（新建漏斗把快照落盘，`NewSessionPersistence.promoteDraft`）
 *   → [ReverseTutorTurnPreparationCoordinator]（算法内核：31 工具 + 全量历史回放）
 * - 无落盘快照的会话（首启欢迎/演示会话、空间导入的历史会话）→ 普通 RAG/图谱链路
 *
 * 判定键的演进（2026-10-18 用户拍板「应用内新建会话一律进内核」）：
 * 原来以 `builtInPresetId == "reverse-tutor"` 为键，但只有「反转家教课堂」这一张预设卡
 * 写得出该值——AI 创建窗口、预设编辑后的「· 副本」、空白自定义草稿、挑战赛 prefill
 * 建出的会话预设 id 均为空，于是全部静默掉进普通链路：没有 31 工具、没有内核状态机，
 * 每轮只带 2 条消息（真机 25 条消息的 prompt tokens 恒定 1300~1400，即此故）。
 *
 * 改成以「落盘快照是否存在」为键后：所有应用内创建的会话（含 8 张预设卡、预设副本、
 * 空白草稿、挑战赛 prefill、窗口分支子会话）都进内核，且**不写任何数据**——预设身份
 * （`builtInPresetId`，兼作 `templateId`／导出标签）保持原值不被覆盖。判定仍是确定性
 * 的（只读请求自带快照，无额外 IO），单一消费点（ChatScreen）零改动。
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
        { request -> request.sessionSnapshot != null },
    private val reversePort: BackgroundTurnPreparationPort,
    private val defaultPort: BackgroundTurnPreparationPort,
    private val coalesceQueuedGeneration: suspend (String) -> Unit = {}
) : BackgroundTurnPreparationPort, TurnMonologuePort {

    private val reverseMonologuePort = reversePort as? TurnMonologuePort

    override suspend fun prepareAndEnqueue(
        request: BackgroundTurnPreparationRequest
    ): BackgroundTurnPreparationResult {
        // 架构单主线收紧（2026-10-18 拍板）：彻底废黜普通链路，所有会话 100% 汇入反转内核。
        // RT 协调器内部提供缺省 RTSession 兜底，并支持 ToolLoop 级智能插队。
        return if (isReverseTutorSession(request)) {
            reversePort.prepareAndEnqueue(request)
        } else {
            // 兼容自定义过滤配置，但缺省统一导向 reversePort
            runCatching { coalesceQueuedGeneration(request.sessionId) }
            defaultPort.prepareAndEnqueue(request)
        }
    }

    override fun getMonologue(jobId: String, token: String): String? =
        reverseMonologuePort?.getMonologue(jobId, token)

    override fun getPreview(jobId: String, token: String): String? =
        reverseMonologuePort?.getPreview(jobId, token)
}
