package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.GuidedLearningIntentClassifier
import com.reversetutor.core.domain.GuidedLearningTurnInput
import com.reversetutor.core.domain.LearnerProfileSnapshot
import com.reversetutor.core.domain.RecentTurnSignals
import com.reversetutor.core.domain.SessionTemplateSnapshot
import com.reversetutor.core.domain.TeachingActionSelector
import com.reversetutor.core.domain.TurnPlan
import com.reversetutor.core.domain.VisibleLearningContext
import com.reversetutor.feature.chat.NewSessionConfiguration

/**
 * NEWMP-V1-002 Task 2.3 / 2.5A · Safe projection from the session template, the
 * window-visible context and the *current turn text* into
 * [GuidedLearningTurnInput].
 *
 * The visible context arrives through the existing trusted chain
 * (`WindowVisibleHistoryReader → TopologyAwareMessageContextPort →
 * ConversationContextAssembler`), which has already enforced fork-boundary,
 * sibling-isolation and space filtering. This mapper consumes that chain's
 * output only: it never re-queries messages, never walks parent/child windows,
 * and copies *identities and structured facts* — never raw text. In particular
 * `recentMessages.text`, `relatedMemory.summary`, `sourceEvidence.excerpt` and
 * `historicalErrors.description` are excluded from every output field.
 *
 * The optional [userText] is fed *only* to the deterministic
 * [GuidedLearningIntentClassifier.classify], which returns a single enum value.
 * The raw text is never written into any snapshot field, the template, the
 * profile, the visible-context id list, evidence, the ledger or any log, so the
 * turn input stays privacy-safe. When [userText] is blank the classifier falls
 * back to [com.reversetutor.core.domain.UserIntent.Ambiguous], preserving the
 * exact pre-2.5A behavior for callers that do not pass it.
 *
 * If the contract's space or session identity does not match the requested
 * (spaceId, sessionId), the whole context is treated as untrusted and the
 * projection degrades to an empty, safe context (template fields remain, as
 * they come from the session's own configuration).
 */
internal fun NewSessionConfiguration?.toGuidedLearningTurnInput(
    spaceId: String,
    sessionId: String,
    context: ConversationContextContract,
    userText: String = "",
    recentSignals: RecentTurnSignals = RecentTurnSignals()
): GuidedLearningTurnInput {
    val trustedContext =
        if (context.spaceId == spaceId && context.sessionId == sessionId) context else null

    val configuration = this
    val conceptSeed = configuration?.goal?.trim().orEmpty()
        .ifEmpty { configuration?.title?.trim().orEmpty() }

    // The current-turn intent is the ONLY thing derived from userText; the raw
    // string never reaches a snapshot field.
    val currentIntent = GuidedLearningIntentClassifier.classify(userText)

    val gapFacts = trustedContext?.prerequisiteGaps ?: emptyList()
    val reviewFacts = trustedContext?.pendingReviewKnowledgePoints ?: emptyList()

    val mappedInput = GuidedLearningTurnInput(
        sessionId = sessionId,
        windowId = sessionId,
        spaceId = spaceId,
        conceptKey = conceptSeed,
        userIntentHint = currentIntent,
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
            recentUserIntent = currentIntent,
            currentConceptKeys = gapFacts + reviewFacts,
            unresolvedQuestionCount = 0
        ),
        conceptStates = emptyMap(),
        recentSignals = recentSignals
    )

    return mappedInput.normalized()
}

/**
 * Concretizes the NEWMP-V1-002 Task 2.4/2.5A chain
 * `mapper input → [TeachingActionSelector.select] → normalized TurnPlan` at the
 * application boundary. The plan produced here is what callers hand to
 * [com.reversetutor.core.data.llm.ChatGenerationInput.turnPlan]; expression
 * only — learning state remains owned by the local verifier.
 */
internal fun GuidedLearningTurnInput.selectGuidedLearningPlan(): TurnPlan =
    TeachingActionSelector.select(this)
