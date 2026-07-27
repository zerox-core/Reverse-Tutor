package com.reversetutor.preview.shell

internal class ChallengeJoinFlowCoordinator(
    private val runtimeCoordinator: ChallengeRuntimeCoordinator,
    private val loadCandidates: suspend () -> List<ChallengeSessionCandidate>
) {
    suspend fun join(): ChallengeSessionLaunchDecision? {
        if (!runtimeCoordinator.join()) return null
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
        val activity = runtimeCoordinator.state.value.activity ?: return null
        val candidates = runCatching { loadCandidates() }.getOrDefault(emptyList())
        return resolveChallengeSessionLaunch(activity, candidates)
    }
}
