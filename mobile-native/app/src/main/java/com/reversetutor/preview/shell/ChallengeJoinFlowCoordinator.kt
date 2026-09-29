package com.reversetutor.preview.shell

internal class ChallengeJoinFlowCoordinator(
    private val runtimeCoordinator: ChallengeRuntimeCoordinator,
    private val loadCandidates: suspend () -> List<ChallengeSessionCandidate>
) {
    suspend fun join(): ChallengeSessionLaunchDecision? {
        if (!runtimeCoordinator.join()) return null
        return postConfirmedJoin()
    }

    suspend fun launchJoinedSession(): ChallengeSessionLaunchDecision? {
        if (!runtimeCoordinator.state.value.joined) return null
        return postConfirmedJoin()
    }

    suspend fun retry(): ChallengeSessionLaunchDecision? {
        val operation = runtimeCoordinator.retry()
        if (operation != ChallengeRuntimeOperation.Join || !runtimeCoordinator.state.value.joined) {
            return null
        }
        return postConfirmedJoin()
    }

    private suspend fun postConfirmedJoin(): ChallengeSessionLaunchDecision? {
        val state = runtimeCoordinator.state.value
        val activity = state.activity ?: return null
        val progress = state.participation?.progress ?: 0L
        val currentTask = activity.tasks.firstOrNull { it.dayNumber == progress + 1L }
        val candidates = runCatching { loadCandidates() }.getOrDefault(emptyList())
        return resolveChallengeSessionLaunch(activity, candidates, currentTask)
    }
}
