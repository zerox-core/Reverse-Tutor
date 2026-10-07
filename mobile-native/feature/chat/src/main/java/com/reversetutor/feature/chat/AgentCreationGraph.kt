package com.reversetutor.feature.chat

/**
 * R100 创建流程状态机（方案B · 2026-10-04 用户拍板落地）。
 *
 * 状态推进：COLLECT_GOAL → INTERVIEW（循环访谈）→ DRAFT_PROPOSAL（孵化草案）
 * → CONFIRM_DRAFT（确认或批注打回）→ GENERATE_PROFILE → GENERATE_LEARNING_FLOW → DONE。
 * 由 [AgentCreationCoordinator] 在 graphEnabled 灰度下驱动；flag 关闭时完全走 R99 契约路径。
 */
enum class CreationState {
    CollectGoal,
    Interview,
    DraftProposal,
    ConfirmDraft,
    GenerateProfile,
    GenerateLearningFlow,
    Done
}

class AgentCreationGraph {

    var state: CreationState = CreationState.CollectGoal
        private set

    /** 合法转移表外的跳转一律抛错——状态机必须在编译期可见的轨道内走。 */
    fun transitionTo(next: CreationState) {
        require(next in TRANSITIONS.getValue(state)) {
            "illegal transition: $state -> $next"
        }
        state = next
    }

    /** 用户发消息进入访谈；CollectGoal → Interview，其余状态保持。 */
    fun noteUserSpoke() {
        if (state == CreationState.CollectGoal) transitionTo(CreationState.Interview)
    }

    /**
     * 流程图生成完毕后用户继续发消息 = 修订：Done → Interview 重开访谈轨道
     * （草案与流程图卡保留，收敛后可再次提案）。
     */
    fun reopenForRevision() {
        if (state == CreationState.Done) transitionTo(CreationState.Interview)
    }

    /**
     * 快照恢复（首版无 checkpoint）：孵化卡 / 流程图卡不落快照，
     * 恢复后只能回到访谈轨道——有对话记录按 Interview、否则 CollectGoal。
     */
    fun restoreFromSnapshot(hasConversation: Boolean) {
        state = if (hasConversation) CreationState.Interview else CreationState.CollectGoal
    }

    private companion object {
        val TRANSITIONS: Map<CreationState, Set<CreationState>> = mapOf(
            CreationState.CollectGoal to setOf(CreationState.Interview),
            CreationState.Interview to setOf(CreationState.Interview, CreationState.DraftProposal),
            CreationState.DraftProposal to setOf(CreationState.ConfirmDraft, CreationState.Interview),
            CreationState.ConfirmDraft to setOf(CreationState.Interview, CreationState.GenerateProfile),
            CreationState.GenerateProfile to setOf(CreationState.GenerateLearningFlow, CreationState.Done),
            CreationState.GenerateLearningFlow to setOf(CreationState.Done),
            CreationState.Done to setOf(CreationState.Interview)
        )
    }
}
