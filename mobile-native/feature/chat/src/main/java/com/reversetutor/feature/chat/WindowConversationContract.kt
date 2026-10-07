package com.reversetutor.feature.chat

import com.reversetutor.core.domain.InitiativePlan
import com.reversetutor.core.domain.MergeDenial
import com.reversetutor.core.domain.ScopeRelation
import com.reversetutor.core.domain.WindowHeartbeatState
import com.reversetutor.core.domain.WindowKind

/**
 * Visual-agnostic window-conversation contract for the chat UI.
 *
 * This is the ONLY type the UI consumes to describe a window's topology,
 * heartbeat, branch-merge eligibility, learning scope, and initiative status.
 * It holds no Room Entity/DAO, no Database, no SecretStore, and no Compose
 * type. It is produced by [WindowConversationFacade] from the pure domain
 * policies in `core:domain`.
 */

data class WindowHeartbeatContract(val state: WindowHeartbeatState)

data class WindowMergeContract(
    val allowed: Boolean,
    val denial: MergeDenial? = null,
    val commitId: String? = null
)

data class WindowScopeContract(
    val relation: ScopeRelation?,
    val reanchorConstraint: String? = null
)

enum class InitiativeStatus { SILENT, HELD, ELIGIBLE }

data class WindowInitiativeContract(
    val status: InitiativeStatus,
    val plan: InitiativePlan? = null
)

data class WindowConversationContract(
    val windowId: String,
    val rootId: String,
    val parentId: String?,
    val kind: WindowKind,
    val heartbeat: WindowHeartbeatContract,
    val merge: WindowMergeContract,
    val scope: WindowScopeContract,
    val initiative: WindowInitiativeContract
)
