package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.GuidedLearningTurnInput
import com.reversetutor.core.domain.LearnerProfileSnapshot
import com.reversetutor.core.domain.RecentTurnSignals
import com.reversetutor.core.domain.SessionTemplateSnapshot
import com.reversetutor.core.domain.TeachingActionSelector
import com.reversetutor.core.domain.TurnPlan
import com.reversetutor.core.domain.UserIntent
import com.reversetutor.core.domain.VisibleLearningContext
import com.reversetutor.feature.chat.NewSessionConfiguration

/**
 * NEWMP-V1-002 Task 2.3 · Safe projection from the session template plus the
 * window-visible context into [GuidedLearningTurnInput].
 *
 * The visible context arrives through the existing trusted chain
 * (`WindowVisibleHistoryReader → TopologyAwareMessageContextPort →
 * ConversationContextAssembler`), which has already enforced fork-boundary,
 * sibling-isolation and space filtering. This mapper consumes that chain's
 * output only: it never re-queries messages, never walks parent/child windows,
 * and copies *identities and structured facts* — never raw text. In particular
 * `recentMessages.text`, `relatedMemory.summary`, `sourceEvidence.excerpt` and
 * `historicalErrors.description` are excluded from every output field, and no
 * learning facts, mastery or intent are inferred from chat text here.
 *
 * If the contract's space or session identity does not match the requested
 * (spaceId, sessionId), the whole context is treated as untrusted and the
 * projection degrades to an empty, safe context (template fields remain, as
 * they come from the session's own configuration).
 */
internal fun NewSessionConfiguration?.toGuidedLearningTurnInput(
    spaceId: String,
    sessionId: String,
    context: ConversationContextContract
): GuidedLearningTurnInput {
    val trustedContext =
        if (context.spaceId == spaceId && context.sessionId == sessionId) context else null

    val configuration = this
    val conceptSeed = configuration?.goal?.trim().orEmpty()
        .ifEmpty { configuration?.title?.trim().orEmpty() }

    val gapFacts = trustedContext?.prerequisiteGaps ?: emptyList()
    val reviewFacts = trustedContext?.pendingReviewKnowledgePoints ?: emptyList()

    val mappedInput = GuidedLearningTurnInput(
        sessionId = sessionId,
        windowId = sessionId,
        spaceId = spaceId,
        conceptKey = conceptSeed,
        userIntentHint = UserIntent.Ambiguous,
        sessionTemplate = SessionTemplateSnapshot(
            subject = configuration?.title.orEmpty(),
            learningGoal = conceptSeed,
            learnerRole = configuration?.learnerRole.orEmpty(),
            dialogueStrategy = configuration?.dialogueStrategy.orEmpty(),
            correctionTiming = configuration?.correctionPersistence.orEmpty()
        ),
        learnerProfile = LearnerProfileSnapshot(
            declaredLevel = configuration?.learnerProfile.orEmpty(),
            preferredTone = configuration?.speakingTone.orEmpty(),
            knownGaps = gapFacts + reviewFacts
        ),
        visibleContext = VisibleLearningContext(
            visibleMessageIds = trustedContext?.recentMessages?.map { it.messageId } ?: emptyList(),
            recentUserIntent = UserIntent.Ambiguous,
            currentConceptKeys = gapFacts + reviewFacts,
            unresolvedQuestionCount = 0
        ),
        conceptStates = emptyMap(),
        recentSignals = RecentTurnSignals()
    )

    return mappedInput.normalized()
}

/**
 * Concretizes the NEWMP-V1-002 Task 2.4 chain
 * `mapper input → [TeachingActionSelector.select] → normalized TurnPlan` at the
 * application boundary. The plan produced here is what callers hand to
 * [com.reversetutor.core.data.llm.ChatGenerationInput.turnPlan]; expression
 * only — learning state remains owned by the local verifier.
 */
internal fun GuidedLearningTurnInput.selectGuidedLearningPlan(): TurnPlan =
    TeachingActionSelector.select(this)
