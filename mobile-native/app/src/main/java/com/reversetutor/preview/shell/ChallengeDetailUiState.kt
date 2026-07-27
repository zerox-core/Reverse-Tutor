package com.reversetutor.preview.shell

internal enum class ChallengeAvailability {
    Loading,
    Available,
    Offline,
    Unavailable
}

internal enum class ChallengeJoinAction {
    Join,
    Retry,
    Loading,
    Joined,
    Unavailable
}

internal data class ChallengeRuleUi(
    val title: String,
    val body: String
)

internal data class ChallengeDetailUiState(
    val title: String,
    val stageLabel: String,
    val goal: String,
    val rules: List<ChallengeRuleUi>,
    val sourcesLabel: String,
    val participationLabel: String,
    val availability: ChallengeAvailability,
    val availabilityLabel: String,
    val joinAction: ChallengeJoinAction
) {
    companion object {
        fun from(runtime: ChallengeRuntimeState?, fallbackJoined: Boolean): ChallengeDetailUiState {
            val activity = runtime?.activity
            val failure = runtime?.failure
            val availability = when {
                runtime?.loading == true && activity == null -> ChallengeAvailability.Loading
                activity != null -> ChallengeAvailability.Available
                failure?.retryable == true -> ChallengeAvailability.Offline
                failure != null -> ChallengeAvailability.Unavailable
                else -> ChallengeAvailability.Offline
            }
            val joined = runtime?.joined ?: fallbackJoined
            val joinAction = when {
                joined -> ChallengeJoinAction.Joined
                runtime?.loading == true -> ChallengeJoinAction.Loading
                failure?.retryable == true -> ChallengeJoinAction.Retry
                failure != null -> ChallengeJoinAction.Unavailable
                availability != ChallengeAvailability.Available -> ChallengeJoinAction.Unavailable
                else -> ChallengeJoinAction.Join
            }
            val stage = when (activity?.state?.lowercase()) {
                "active" -> "进行中"
                "upcoming" -> "即将开始"
                "ended" -> "已结束"
                null -> if (availability == ChallengeAvailability.Offline) "离线" else "不可用"
                else -> activity.state
            }
            val participation = when {
                joined -> "已加入 · 进度 ${runtime?.participation?.progress ?: 0}"
                failure?.operation == ChallengeRuntimeOperation.Join -> "加入未确认，详情与进度未改变"
                else -> "尚未加入"
            }
            return ChallengeDetailUiState(
                title = activity?.title?.takeIf(String::isNotBlank) ?: "活动暂不可用",
                stageLabel = stage,
                goal = activity?.description?.takeIf(String::isNotBlank)
                    ?: "活动服务未提供目标说明。",
                rules = listOf(
                    ChallengeRuleUi(
                        "参与确认",
                        if (activity?.requiresOnlineConfirmation == true) {
                            "加入需要在线确认，未确认前不会改变参与状态。"
                        } else {
                            "参与状态以活动服务返回的确认结果为准。"
                        }
                    ),
                    ChallengeRuleUi(
                        "进度记录",
                        if (activity?.allowsDeferredProgress == true) {
                            "离线进度可延后提交，最终状态以同步确认结果为准。"
                        } else {
                            "当前活动不支持离线延后提交。"
                        }
                    ),
                    ChallengeRuleUi(
                        "详细规则",
                        "当前活动契约未提供规则正文，暂不展示推测内容。"
                    )
                ),
                sourcesLabel = activity?.sessionTemplateId
                    ?.takeIf(String::isNotBlank)
                    ?.let { "会话模板：$it" }
                    ?: "活动服务未提供会话模板或资料来源。",
                participationLabel = participation,
                availability = availability,
                availabilityLabel = when (availability) {
                    ChallengeAvailability.Loading -> "正在加载活动"
                    ChallengeAvailability.Available -> "活动信息已确认"
                    ChallengeAvailability.Offline -> "当前离线，无法确认活动信息"
                    ChallengeAvailability.Unavailable -> "当前没有可用活动"
                },
                joinAction = joinAction
            )
        }
    }
}
