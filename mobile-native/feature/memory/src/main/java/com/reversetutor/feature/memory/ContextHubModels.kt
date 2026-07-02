package com.reversetutor.feature.memory

enum class ContextHubSection(
    val label: String
) {
    Overview("Overview"),
    Graph("Graph"),
    Anchors("Anchors"),
    Notes("Notes"),
    Errors("Errors"),
    SessionSettings("Session settings")
}

data class ContextHubUiState(
    val sessionId: String?,
    val sessionTitle: String,
    val sessionStatusLabel: String,
    val overviewLines: List<String>,
    val graphState: KnowledgeGraphUiState,
    val sections: List<ContextHubSectionState>
) {
    val hasActiveSession: Boolean
        get() = !sessionId.isNullOrBlank()

    companion object {
        fun fromActiveSession(
            sessionId: String?,
            sessionTitle: String?
        ): ContextHubUiState {
            val normalizedTitle = sessionTitle?.trim().takeUnless { it.isNullOrBlank() }
            val active = !sessionId.isNullOrBlank() && normalizedTitle != null
            return ContextHubUiState(
                sessionId = sessionId,
                sessionTitle = normalizedTitle ?: "No active session",
                sessionStatusLabel = if (active) {
                    "Session-linked context shell"
                } else {
                    "Open a session from Sessions before using context evidence."
                },
                overviewLines = listOf(
                    "Scope: per-session evidence hub",
                    "Graph engine: native Canvas is available; stored graph data may still be empty",
                    "Data state: empty shell; no legacy graph parity is claimed"
                ),
                graphState = KnowledgeGraphUiState.from(
                    nodes = emptyList(),
                    edges = emptyList()
                ),
                sections = ContextHubSection.entries.map { it.toState() }
            )
        }

        fun fromMemorySnapshot(
            sessionId: String?,
            sessionTitle: String?,
            snapshot: ContextMemorySnapshot,
            graphState: KnowledgeGraphUiState? = null
        ): ContextHubUiState {
            val base = fromActiveSession(sessionId = sessionId, sessionTitle = sessionTitle)
            val resolvedGraphState = graphState ?: base.graphState
            return base.copy(
                sessionStatusLabel = if (base.hasActiveSession) {
                    "Session-linked memory evidence"
                } else {
                    base.sessionStatusLabel
                },
                overviewLines = listOf(
                    "Anchors: ${snapshot.anchors.size}",
                    "Notes: ${snapshot.notes.size}",
                    "Open errors: ${snapshot.errors.count { !it.resolved }}",
                    "Graph nodes: ${resolvedGraphState.nodes.size}",
                    "Graph edges: ${resolvedGraphState.edgeCount}",
                    "Native graph status: ${resolvedGraphState.status.label}"
                ),
                graphState = resolvedGraphState,
                sections = ContextHubSection.entries.map { section ->
                    section.toState(snapshot, resolvedGraphState)
                }
            )
        }
    }
}

data class ContextMemorySnapshot(
    val anchors: List<ContextMemoryEntry> = emptyList(),
    val notes: List<ContextMemoryEntry> = emptyList(),
    val errors: List<ContextErrorEntry> = emptyList()
)

data class ContextMemoryEntry(
    val id: String,
    val title: String,
    val body: String,
    val sourceMessageId: String? = null,
    val sourceId: String? = null
)

data class ContextErrorEntry(
    val id: String,
    val title: String,
    val detail: String,
    val sourceMessageId: String? = null,
    val resolved: Boolean = false
)

data class ContextHubSectionState(
    val section: ContextHubSection,
    val statusLabel: String,
    val title: String,
    val body: String,
    val nextActions: List<String>
)

private fun ContextHubSection.toState(): ContextHubSectionState =
    when (this) {
        ContextHubSection.Overview -> ContextHubSectionState(
            section = this,
            statusLabel = "Empty",
            title = "Session evidence overview",
            body = "Learning summary, active anchors, recent evidence, and source status appear here after native memory extraction lands.",
            nextActions = listOf("Continue chat", "Import sources when the Sources module is ready")
        )
        ContextHubSection.Graph -> ContextHubSectionState(
            section = this,
            statusLabel = GraphRenderStatus.Empty.label,
            title = "No graph nodes yet",
            body = "Native Canvas graph rendering is available. Store graph nodes and relations to render them here.",
            nextActions = listOf("Create memory evidence", "Import sources for graphable context")
        )
        ContextHubSection.Anchors -> ContextHubSectionState(
            section = this,
            statusLabel = "Empty",
            title = "No anchors yet",
            body = "Requirements, source anchors, and imported materials will appear after anchor persistence and source linking land.",
            nextActions = listOf("Keep evidence in chat for now", "Add anchors after P5 anchor work")
        )
        ContextHubSection.Notes -> ContextHubSectionState(
            section = this,
            statusLabel = "Empty",
            title = "No notes yet",
            body = "Notes created from chat messages and editable notes are deferred to the memory persistence task.",
            nextActions = listOf("Use chat quote for short-term evidence", "Create notes after P5 note work")
        )
        ContextHubSection.Errors -> ContextHubSectionState(
            section = this,
            statusLabel = "Empty",
            title = "No error history yet",
            body = "Misconception and error evidence will appear after error-log persistence and review flows land.",
            nextActions = listOf("Keep corrections in chat", "Review errors after P5 error work")
        )
        ContextHubSection.SessionSettings -> ContextHubSectionState(
            section = this,
            statusLabel = "Deferred",
            title = "Session settings deferred",
            body = "Persona, strategy, deadline, avatar, and change warning controls are represented here as a surface, but editing belongs to the session settings task.",
            nextActions = listOf("Use Settings for global profiles", "Return after session settings persistence lands")
        )
    }

private fun ContextHubSection.toState(
    snapshot: ContextMemorySnapshot,
    graphState: KnowledgeGraphUiState
): ContextHubSectionState =
    when (this) {
        ContextHubSection.Overview -> toState()
        ContextHubSection.Graph -> ContextHubSectionState(
            section = this,
            statusLabel = graphState.status.label,
            title = graphState.title,
            body = graphState.summary,
            nextActions = graphState.nextActions()
        )
        ContextHubSection.SessionSettings -> toState()
        ContextHubSection.Anchors -> {
            if (snapshot.anchors.isEmpty()) {
                toState()
            } else {
                ContextHubSectionState(
                    section = this,
                    statusLabel = snapshot.anchors.countLabel("anchor", "anchors"),
                    title = "Anchors",
                    body = snapshot.anchors.joinToString("\n\n") { it.toBodyLine() },
                    nextActions = listOf("Review source evidence", "Link anchors into graph after graph work lands")
                )
            }
        }
        ContextHubSection.Notes -> {
            if (snapshot.notes.isEmpty()) {
                toState()
            } else {
                ContextHubSectionState(
                    section = this,
                    statusLabel = snapshot.notes.countLabel("note", "notes"),
                    title = "Notes",
                    body = snapshot.notes.joinToString("\n\n") { it.toBodyLine() },
                    nextActions = listOf("Open linked chat message when jump support lands", "Edit or delete notes from memory actions")
                )
            }
        }
        ContextHubSection.Errors -> {
            if (snapshot.errors.isEmpty()) {
                toState()
            } else {
                val openCount = snapshot.errors.count { !it.resolved }
                ContextHubSectionState(
                    section = this,
                    statusLabel = if (openCount == 1) "1 open" else "$openCount open",
                    title = "Errors",
                    body = snapshot.errors.joinToString("\n\n") { it.toBodyLine() },
                    nextActions = listOf("Resolve after correction evidence", "Connect to diagnostics review after Phase 3")
                )
            }
        }
    }

private fun KnowledgeGraphUiState.nextActions(): List<String> =
    when (status) {
        GraphRenderStatus.Loading -> listOf("Wait for graph snapshot")
        GraphRenderStatus.Empty -> listOf("Create notes or anchors", "Import sources for graphable evidence")
        GraphRenderStatus.Ready -> listOf("Select nodes for detail", "Pan or zoom the native graph")
        GraphRenderStatus.Invalid -> listOf("Review invalid relations", "Select valid nodes for evidence")
        GraphRenderStatus.Large -> listOf("Use node list for precise selection", "Cluster and filtering remain follow-up work")
    }

private fun List<ContextMemoryEntry>.countLabel(
    singular: String,
    plural: String
): String =
    "${size} ${if (size == 1) singular else plural}"

private fun ContextMemoryEntry.toBodyLine(): String =
    buildString {
        append(title)
        append(": ")
        append(body)
        sourceMessageId?.let { append("\nMessage: ").append(it) }
        sourceId?.let { append("\nSource: ").append(it) }
    }

private fun ContextErrorEntry.toBodyLine(): String =
    buildString {
        append(title)
        append(": ")
        append(detail)
        append("\nStatus: ")
        append(if (resolved) "resolved" else "open")
        sourceMessageId?.let { append("\nMessage: ").append(it) }
    }
