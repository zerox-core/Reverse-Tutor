package com.reversetutor.feature.chat

import com.reversetutor.core.domain.CompanionMemoryEvolutionPolicy
import com.reversetutor.core.domain.InitiativeEligibilityInput
import com.reversetutor.core.domain.InitiativeEligibilityPolicy
import com.reversetutor.core.domain.InitiativeDecision
import com.reversetutor.core.domain.LearningIntentEnvelope
import com.reversetutor.core.domain.LearningScopeGuard
import com.reversetutor.core.domain.ScopeSignal
import com.reversetutor.core.domain.WindowHeartbeatState
import com.reversetutor.core.domain.WindowMergeDecision
import com.reversetutor.core.domain.WindowRef
import com.reversetutor.core.domain.WindowTopologyPolicy

/**
 * Compose-free window-conversation facade.
 *
 * It is a pure projection of the `core:domain` topology/memory/scope/initiative
 * policies into [WindowConversationContract] slices. It never creates a Worker,
 * never writes a message, and never decides persistence. It is the stable entry
 * point for the chat UI to read window topology and heartbeat/initiative state.
 */
class WindowConversationFacade {

    fun heartbeat(state: WindowHeartbeatState): WindowHeartbeatContract =
        WindowHeartbeatContract(state)

    fun defaultHeartbeat(window: WindowRef): WindowHeartbeatContract =
        WindowHeartbeatContract(WindowTopologyPolicy.defaultHeartbeatState(window.kind))

    fun enableChildHeartbeat(state: WindowHeartbeatState): WindowHeartbeatContract =
        WindowHeartbeatContract(WindowTopologyPolicy.enableHeartbeat(state))

    fun merge(
        child: WindowRef,
        parent: WindowRef,
        deltaId: String,
        sourceRevision: Long
    ): WindowMergeContract = when (val decision = WindowTopologyPolicy.mergeDecision(child, parent, deltaId, sourceRevision)) {
        is WindowMergeDecision.Allowed -> WindowMergeContract(allowed = true, commitId = decision.commit.id)
        is WindowMergeDecision.Denied -> WindowMergeContract(allowed = false, denial = decision.reason)
    }

    fun scope(
        window: WindowRef,
        envelope: LearningIntentEnvelope?,
        signals: List<ScopeSignal>
    ): WindowScopeContract {
        if (!LearningScopeGuard.forWindow(window, envelope)) {
            return WindowScopeContract(relation = null)
        }
        val decision = LearningScopeGuard.classify(envelope!!, signals)
        return WindowScopeContract(relation = decision.relation, reanchorConstraint = decision.reanchorConstraint)
    }

    fun initiative(input: InitiativeEligibilityInput, nowEpochMillis: Long): WindowInitiativeContract =
        when (val decision = InitiativeEligibilityPolicy.decide(input, nowEpochMillis)) {
            InitiativeDecision.Silent -> WindowInitiativeContract(InitiativeStatus.SILENT)
            InitiativeDecision.Held -> WindowInitiativeContract(InitiativeStatus.HELD)
            is InitiativeDecision.Eligible -> WindowInitiativeContract(InitiativeStatus.ELIGIBLE, decision.plan)
        }

    /**
     * Compose a single [WindowConversationContract] snapshot for a window.
     * `heartbeatState` and the other area inputs are provided by the caller;
     * the facade only projects policy outcomes and never performs I/O.
     */
    fun project(
        window: WindowRef,
        heartbeatState: WindowHeartbeatState,
        merge: WindowMergeContract,
        scope: WindowScopeContract,
        initiative: WindowInitiativeContract
    ): WindowConversationContract = WindowConversationContract(
        windowId = window.id,
        rootId = window.rootId,
        parentId = window.parentId,
        kind = window.kind,
        heartbeat = heartbeat(heartbeatState),
        merge = merge,
        scope = scope,
        initiative = initiative
    )
}
