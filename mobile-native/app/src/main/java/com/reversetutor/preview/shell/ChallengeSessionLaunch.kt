package com.reversetutor.preview.shell

import com.reversetutor.core.domain.ActivitySummary
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionPrefillRequest
import com.reversetutor.feature.chat.canonicalActivitySource
import com.reversetutor.feature.chat.normalizeActivitySource
import java.util.Locale

internal data class ChallengeSessionCandidate(
    val sessionId: String,
    val title: String,
    val learnerRole: String,
    val updatedAtEpochMillis: Long,
    val snapshot: NewSessionConfiguration?
)

internal sealed interface ChallengeSessionLaunchDecision {
    data class Reuse(val candidate: ChallengeSessionCandidate) : ChallengeSessionLaunchDecision
    data class Create(val prefill: NewSessionPrefillRequest) : ChallengeSessionLaunchDecision
}

internal fun resolveChallengeSessionLaunch(
    activity: ActivitySummary,
    candidates: List<ChallengeSessionCandidate>
): ChallengeSessionLaunchDecision {
    val canonicalSource = canonicalActivitySource(activity.id)
    val existing = candidates
        .filter { candidate ->
            candidate.snapshot?.sourceSelections.orEmpty()
                .mapNotNull(::normalizeActivitySource)
                .any { it == canonicalSource }
        }
        .maxByOrNull(ChallengeSessionCandidate::updatedAtEpochMillis)
    if (existing != null) return ChallengeSessionLaunchDecision.Reuse(existing)

    val goal = activity.description.trim().ifEmpty { "完成挑战目标并持续复盘学习进度" }
    return ChallengeSessionLaunchDecision.Create(
        NewSessionPrefillRequest(
            requestId = "challenge-${activity.id.trim().lowercase(Locale.ROOT)}-${activity.revision}",
            configuration = NewSessionConfiguration(
                title = "${activity.title}学习会话",
                learnerRole = "挑战学习伙伴",
                learnerProfile = "围绕当前挑战追问依据、检查进度并协助复盘。",
                goal = goal,
                plan = "按挑战节奏完成学习、讲解与复盘。",
                dialogueStrategy = "用户负责讲解，学习伙伴持续追问并核对挑战目标。",
                sourceSelections = listOf(canonicalSource),
                openingMessage = "我们从今天的挑战目标开始吧，请先讲讲你的计划。"
            )
        )
    )
}
