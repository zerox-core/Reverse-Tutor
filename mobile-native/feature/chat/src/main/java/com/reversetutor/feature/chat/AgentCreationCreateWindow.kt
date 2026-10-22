package com.reversetutor.feature.chat

/**
 * 创建成功后的 3 秒可撤销窗口（2026-10-15 用户拍板规格）。
 *
 * 创建成功后停留 3 秒（倒计时）提供「撤销 / 改草案」：
 * · 撤销 → 回到草案页并恢复刚才的草案内容（草案不丢）；
 * · 改草案 → 会话保留，同样回草案页继续改；
 * · 3 秒内未操作 → 浮层消失、会话保留（进入会话窗口）。
 *
 * 纯逻辑 + 注入时钟，可单测；UI 只负责渲染倒计时并按 [Decision] 决定导航。
 */
class AgentCreationCreateWindow(
    private val durationMillis: Long = DEFAULT_DURATION_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    /** 倒计时中的一次创建：会话 + 创建那一刻的草案快照。 */
    data class Pending(
        val session: SessionListItem,
        val draft: NewSessionConfiguration,
        val openedAtEpochMillis: Long,
        val deadlineEpochMillis: Long
    ) {
        fun remainingMillis(nowEpochMillis: Long): Long =
            (deadlineEpochMillis - nowEpochMillis).coerceAtLeast(0L)
    }

    /** 窗口结束后的去向。 */
    sealed interface Decision {
        /** 保留会话（3 秒内未操作）→ 进入会话窗口。 */
        data class KeepSession(val session: SessionListItem) : Decision

        /** 回到草案页并恢复草案内容（撤销 / 改草案）。 */
        data class RestoreDraft(
            val draft: NewSessionConfiguration,
            val session: SessionListItem,
            val reason: RestoreReason
        ) : Decision
    }

    enum class RestoreReason { Undo, EditDraft }

    var pending: Pending? = null
        private set

    val active: Boolean get() = pending != null

    /** 创建成功：开启 3 秒可撤销窗口（草案按快照保存，之后改草案不影响恢复内容）。 */
    fun open(session: SessionListItem, draft: NewSessionConfiguration): Pending {
        val openedAt = nowMillis()
        val next = Pending(
            session = session,
            draft = draft.deepCopy(),
            openedAtEpochMillis = openedAt,
            deadlineEpochMillis = openedAt + durationMillis
        )
        pending = next
        return next
    }

    /** 倒计时剩余毫秒（窗口关闭时 0）。 */
    fun remainingMillis(): Long = pending?.remainingMillis(nowMillis()) ?: 0L

    fun expired(): Boolean = pending?.let { nowMillis() >= it.deadlineEpochMillis } ?: false

    /** 用户主动点击「确定进入会话」：确认保留并关闭窗口。 */
    fun confirmKeep(): Decision.KeepSession? {
        val current = pending ?: return null
        pending = null
        return Decision.KeepSession(current.session)
    }

    /** 倒计时到期未操作：浮层消失、会话保留。未到期返回 null。 */
    fun expireIfDue(): Decision.KeepSession? {
        val current = pending ?: return null
        if (nowMillis() < current.deadlineEpochMillis) return null
        pending = null
        return Decision.KeepSession(current.session)
    }

    /** 用户点「撤销」：回到草案页并恢复刚才的草案内容；过期后不再提供（返回 null）。 */
    fun undo(): Decision.RestoreDraft? = close(RestoreReason.Undo)

    /** 用户点「改草案」：会话保留，回草案页继续改；过期后不再提供（返回 null）。 */
    fun editDraft(): Decision.RestoreDraft? = close(RestoreReason.EditDraft)

    private fun close(reason: RestoreReason): Decision.RestoreDraft? {
        val current = pending ?: return null
        if (nowMillis() >= current.deadlineEpochMillis) return null
        pending = null
        return Decision.RestoreDraft(
            draft = current.draft.deepCopy(),
            session = current.session,
            reason = reason
        )
    }

    companion object {
        /** 可撤销窗口时长：3 秒（用户拍板）。 */
        const val DEFAULT_DURATION_MILLIS = 3_000L
    }
}
