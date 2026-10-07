package com.reversetutor.preview.shell

internal enum class ChallengeReturnPhase {
    Locked,
    ArmedUntilGestureEnds,
    EligibleForNextGesture
}

internal data class ChallengeVerticalState(
    val returnPhase: ChallengeReturnPhase = ChallengeReturnPhase.Locked,
    val contentAtBottom: Boolean = false,
    val contentGestureActive: Boolean = false,
    val detailOpen: Boolean = false
) {
    val listScrollEnabled: Boolean
        get() = !detailOpen

    val outerPagerEnabled: Boolean
        get() = !detailOpen && returnPhase == ChallengeReturnPhase.EligibleForNextGesture
}

internal sealed interface ChallengeVerticalEvent {
    data object Entered : ChallengeVerticalEvent
    data class ContentScrollChanged(
        val atBottom: Boolean,
        val gestureActive: Boolean
    ) : ChallengeVerticalEvent
    data object DetailOpened : ChallengeVerticalEvent
    data object DetailClosed : ChallengeVerticalEvent
}

internal fun reduceChallengeVerticalState(
    state: ChallengeVerticalState,
    event: ChallengeVerticalEvent
): ChallengeVerticalState = when (event) {
    ChallengeVerticalEvent.Entered -> ChallengeVerticalState()
    ChallengeVerticalEvent.DetailOpened -> state.copy(
        detailOpen = true,
        returnPhase = if (
            state.returnPhase == ChallengeReturnPhase.ArmedUntilGestureEnds
        ) {
            ChallengeReturnPhase.Locked
        } else {
            state.returnPhase
        }
    )
    ChallengeVerticalEvent.DetailClosed -> state.copy(detailOpen = false)
    is ChallengeVerticalEvent.ContentScrollChanged -> {
        val phase = when {
            !event.atBottom -> ChallengeReturnPhase.Locked
            state.returnPhase == ChallengeReturnPhase.EligibleForNextGesture ->
                ChallengeReturnPhase.EligibleForNextGesture
            state.returnPhase == ChallengeReturnPhase.ArmedUntilGestureEnds &&
                !event.gestureActive -> ChallengeReturnPhase.EligibleForNextGesture
            event.gestureActive -> ChallengeReturnPhase.ArmedUntilGestureEnds
            else -> ChallengeReturnPhase.Locked
        }
        state.copy(
            returnPhase = phase,
            contentAtBottom = event.atBottom,
            contentGestureActive = event.gestureActive
        )
    }
}

internal object ChallengePagerPolicy {
    const val EntryPositionalThreshold = 0.65f
}
