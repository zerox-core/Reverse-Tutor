package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.InitiativeEligibilityInput
import com.reversetutor.core.domain.LearningIntentEnvelope
import com.reversetutor.core.domain.ScopeSignal
import com.reversetutor.core.domain.WindowHeartbeatState
import com.reversetutor.core.domain.WindowRef
import com.reversetutor.feature.chat.InitiativeStatus
import com.reversetutor.feature.chat.WindowConversationContract
import com.reversetutor.feature.chat.WindowConversationFacade
import com.reversetutor.feature.chat.WindowHeartbeatContract
import com.reversetutor.feature.chat.WindowInitiativeContract
import com.reversetutor.feature.chat.WindowMergeContract
import com.reversetutor.feature.chat.WindowScopeContract

/**
 * Receipt for a future window-conversation dispatch. Package B intentionally
 * does not persist the topology or dispatch a heartbeat job; this seam returns
 * the explicit "not persisted yet" state so the UI never misreads the result.
 */
data class WindowDispatchReceipt(
    val targetWindowId: String,
    val persisted: Boolean = false,
    val note: String = "not persisted until P6"
)

/**
 * Compose-free app-wiring root for window topology and initiative.
 *
 * It composes only the pure `core:domain` policies (via [WindowConversationFacade])
 * and read-only topology/memory/scope/initiative ports injected by the caller.
 * It never creates a Worker, never writes a message, never calls
 * `SessionConversationAssembly.runTurn()`, and never calls
 * `ChatGenerationRepository.generateReply()`. Eligibility evaluation is a pure
 * projection over read ports; the only write-related surface is the future
 * [WindowDispatchReceipt] seam, which reports "not persisted yet".
 */
class WindowConversationAssembly(
    private val readWindow: (windowId: String) -> WindowRef,
    private val readHeartbeatState: (windowId: String) -> WindowHeartbeatState,
    private val readScope: (windowId: String) -> Pair<LearningIntentEnvelope?, List<ScopeSignal>>,
    private val readEligibilityInput: (windowId: String) -> InitiativeEligibilityInput,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val facade: WindowConversationFacade = WindowConversationFacade()
) {

    fun heartbeat(windowId: String): WindowHeartbeatContract =
        facade.defaultHeartbeat(readWindow(windowId))

    fun enableChildHeartbeat(windowId: String): WindowHeartbeatContract =
        facade.enableChildHeartbeat(readHeartbeatState(windowId))

    fun merge(childId: String, parentId: String, deltaId: String, sourceRevision: Long): WindowMergeContract =
        facade.merge(readWindow(childId), readWindow(parentId), deltaId, sourceRevision)

    fun scope(windowId: String): WindowScopeContract {
        val (envelope, signals) = readScope(windowId)
        return facade.scope(readWindow(windowId), envelope, signals)
    }

    fun initiative(windowId: String): WindowInitiativeContract =
        facade.initiative(readEligibilityInput(windowId), nowEpochMillis())

    /**
     * Compose a single [WindowConversationContract] snapshot for a window.
     */
    fun project(windowId: String): WindowConversationContract =
        facade.project(
            window = readWindow(windowId),
            heartbeatState = readHeartbeatState(windowId),
            merge = WindowMergeContract(allowed = false),
            scope = scope(windowId),
            initiative = initiative(windowId)
        )

    /**
     * Future dispatch seam. Package B never persists or dispatches; it returns an
     * explicit [WindowDispatchReceipt] with `persisted = false`.
     */
    fun prepareDispatch(windowId: String): WindowDispatchReceipt =
        WindowDispatchReceipt(targetWindowId = windowId)
}

/** Convenience helper to read an initiative status from a contract. */
fun WindowInitiativeContract.isEligible(): Boolean = status == InitiativeStatus.ELIGIBLE
