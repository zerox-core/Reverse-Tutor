package com.reversetutor.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val SessionSettingsUndoWindowMillis: Long = 5_000L

enum class SessionSettingsSection(val label: String) {
    Basic("基本资料"),
    GoalPlan("学习目标与计划"),
    ConversationStrategy("对话策略"),
    SourceManagement("资料管理"),
    WorldTree("世界树配置"),
    Danger("危险操作")
}

enum class ConversationStrategyControl(val label: String) {
    Feedback("反馈强度"),
    Probing("追问强度"),
    Scaffolding("脚手架强度"),
    CorrectionPersistence("纠错坚持度"),
    ReviewFrequency("复习频率"),
    SpeakingTone("说话语气")
}

enum class SourceDetailAction(val label: String) {
    Reselect("重新选择文件"),
    Unlink("取消本会话引用"),
    DeleteFile("删除资料文件")
}

enum class DangerousSessionAction(val label: String) {
    DeleteCurrentSession("删除当前会话")
}

sealed interface SourceFileDeleteResult {
    data object Deleted : SourceFileDeleteResult
    data class Failed(val message: String) : SourceFileDeleteResult
}

interface SourceFileDeleteCapability {
    val available: Boolean
    fun delete(sourceId: String): SourceFileDeleteResult

    data object Unavailable : SourceFileDeleteCapability {
        override val available: Boolean = false
        override fun delete(sourceId: String): SourceFileDeleteResult =
            SourceFileDeleteResult.Failed("当前版本暂不支持删除资料文件。")
    }
}

class Task2ADeletionDelegate(
    private val request: () -> Unit,
    private val confirm: () -> Unit,
    private val dismiss: () -> Unit,
    private val undo: () -> Unit
) {
    fun request() = request.invoke()
    fun confirm() = confirm.invoke()
    fun dismiss() = dismiss.invoke()
    fun undo() = undo.invoke()
}

data class ChatScrollPosition(val index: Int = 0, val offset: Int = 0)

class ChatScrollMemory {
    private val positions = mutableMapOf<String, ChatScrollPosition>()

    fun capture(sessionId: String, position: ChatScrollPosition) {
        positions[sessionId] = position
    }

    fun restore(sessionId: String): ChatScrollPosition = positions[sessionId] ?: ChatScrollPosition()
}

data class SessionSettingsProfile(
    val title: String,
    val learnerDisplayName: String,
    val learnerRole: String,
    val avatarVisible: Boolean = true,
    val personality: String,
    val interactionHabits: String
)

data class SessionGoalPlan(
    val primaryGoal: String,
    val deadline: String = "未设置",
    val learningScope: String = "未设置",
    val modules: String = "未设置",
    val stageMilestones: String = "未设置",
    val weeklyPlan: String = "未设置",
    val currentState: String = "未设置"
)

data class ConversationStrategy(
    val feedbackIntensity: Int = 3,
    val probingIntensity: Int = 3,
    val scaffoldingIntensity: Int = 3,
    val correctionPersistence: String = "适中",
    val reviewFrequency: String = "每周",
    val speakingTone: String = "自然"
)

data class SessionSettingsDocument(
    val profile: SessionSettingsProfile,
    val goalPlan: SessionGoalPlan,
    val strategy: ConversationStrategy = ConversationStrategy(),
    val snapshot: NewSessionConfiguration,
    val quickTags: Map<String, TagFieldSelection> = emptyMap()
) {
    fun deepCopy(): SessionSettingsDocument = copy(
        snapshot = snapshot.deepCopy(),
        quickTags = quickTags.mapValues { it.value.deepCopy() }
    )

    companion object {
        fun fromSnapshot(snapshot: NewSessionConfiguration): SessionSettingsDocument {
            val copy = snapshot.deepCopy()
            return SessionSettingsDocument(
                profile = SessionSettingsProfile(
                    title = copy.title,
                    learnerDisplayName = copy.learnerDisplayName,
                    learnerRole = copy.learnerRole,
                    avatarVisible = copy.avatarVisible,
                    personality = copy.learnerProfile,
                    interactionHabits = copy.dialogueStrategy
                ),
                goalPlan = SessionGoalPlan(
                    primaryGoal = copy.goal,
                    deadline = copy.deadline,
                    learningScope = copy.learningScope,
                    modules = copy.modules,
                    stageMilestones = copy.stageMilestones,
                    weeklyPlan = copy.plan.ifBlank { "未设置" },
                    currentState = copy.currentState
                ),
                strategy = ConversationStrategy(
                    feedbackIntensity = copy.feedbackIntensity,
                    probingIntensity = copy.probingIntensity,
                    scaffoldingIntensity = copy.scaffoldingIntensity,
                    correctionPersistence = copy.correctionPersistence,
                    reviewFrequency = copy.reviewFrequency,
                    speakingTone = copy.speakingTone
                ),
                snapshot = copy,
                quickTags = copy.quickTags.mapValues { it.value.deepCopy() }
            )
        }
    }
}

enum class SourceReadState(val label: String) {
    Ready("正常"),
    Processing("处理中"),
    Invalid("失效")
}

enum class SourceFilter(val label: String) {
    All("全部"),
    Ready("正常"),
    Processing("处理中"),
    Invalid("失效")
}

data class SessionSource(
    val id: String,
    val displayName: String,
    val managedName: String,
    val typeLabel: String,
    val readState: SourceReadState,
    val currentSessionReferenced: Boolean,
    val referenceOwnerIds: List<String>,
    val lastUsedAtEpochMillis: Long,
    val preview: String,
    val bytesRetained: Boolean,
    val previousRevisionId: String? = null,
    val deletionPending: Boolean = false
)

data class ProtectedChangeConfirmation(
    val differences: List<ConfigurationDifference>
)

data class PendingSourceDelete(
    val sourceId: String,
    val impactedOwnerIds: List<String>,
    val deleteEnabled: Boolean,
    val requiresImpactConfirmation: Boolean = false
)

enum class SourceUndoKind {
    Unlink,
    DeleteFile
}

data class SourceUndo(
    val kind: SourceUndoKind,
    val sourceId: String,
    val previous: SessionSource,
    val expiresAtEpochMillis: Long,
    val previousSelectionIds: List<String>
)

data class SessionSettingsStoredState(
    val applied: SessionSettingsDocument,
    val form: SessionSettingsDocument,
    val sources: List<SessionSource>,
    val pendingConfirmation: ProtectedChangeConfirmation? = null,
    val pendingSourceDelete: PendingSourceDelete? = null,
    val sourceUndo: SourceUndo? = null,
    val errorMessage: String? = null,
    val sourceLastUsedAt: Map<String, Long> = emptyMap()
) {
    fun deepCopy(): SessionSettingsStoredState = copy(
        applied = applied.deepCopy(),
        form = form.deepCopy(),
        sources = sources.map { it.copy(referenceOwnerIds = it.referenceOwnerIds.toList()) },
        pendingConfirmation = pendingConfirmation?.copy(differences = pendingConfirmation.differences.toList()),
        pendingSourceDelete = pendingSourceDelete?.copy(impactedOwnerIds = pendingSourceDelete.impactedOwnerIds.toList()),
        sourceUndo = sourceUndo?.copy(previous = sourceUndo.previous.copy(
            referenceOwnerIds = sourceUndo.previous.referenceOwnerIds.toList()
        ), previousSelectionIds = sourceUndo.previousSelectionIds.toList()),
        sourceLastUsedAt = LinkedHashMap(sourceLastUsedAt)
    )
}

interface SessionSettingsStore {
    fun load(sessionId: String): SessionSettingsStoredState?
    fun save(sessionId: String, state: SessionSettingsStoredState)
    fun sourceLastUsedAt(sessionId: String): Map<String, Long> =
        load(sessionId)?.sourceLastUsedAt.orEmpty()
    fun recordSourcesUsed(sessionId: String, sourceIds: Set<String>, usedAtEpochMillis: Long) {
        if (sourceIds.isEmpty()) return
        val current = load(sessionId) ?: return
        val recordedUsage = sourceIds.associateWith { sourceId ->
            maxOf(current.sourceLastUsedAt[sourceId] ?: Long.MIN_VALUE, usedAtEpochMillis)
        }
        save(
            sessionId,
            current.copy(
                sources = current.sources.map { source ->
                    recordedUsage[source.id]?.let { usedAt ->
                        source.copy(lastUsedAtEpochMillis = maxOf(source.lastUsedAtEpochMillis, usedAt))
                    } ?: source
                },
                sourceLastUsedAt = current.sourceLastUsedAt + recordedUsage
            )
        )
    }
    fun remove(sessionId: String) = Unit
}

class InMemorySessionSettingsStore : SessionSettingsStore {
    private val states = mutableMapOf<String, SessionSettingsStoredState>()

    override fun load(sessionId: String): SessionSettingsStoredState? = states[sessionId]?.deepCopy()

    override fun save(sessionId: String, state: SessionSettingsStoredState) {
        states[sessionId] = state.deepCopy()
    }

    override fun remove(sessionId: String) {
        states.remove(sessionId)
    }
}

class SessionSettingsCoordinator(
    private val sessionId: String,
    initial: SessionSettingsDocument,
    initialSources: List<SessionSource>,
    private val store: SessionSettingsStore,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    val deleteCapability: SourceFileDeleteCapability = SourceFileDeleteCapability.Unavailable,
    private val onSnapshotApplied: (NewSessionConfiguration) -> Unit = {},
    private val onSourcesChanged: () -> Unit = {}
) {
    private var lastPublishedSnapshot: NewSessionConfiguration? = null
    private val restored = store.load(sessionId)
    var state: SessionSettingsStoredState by mutableStateOf(
        (restored?.let { saved ->
            if (saved.sources.isEmpty() && initialSources.isNotEmpty()) {
                saved.copy(sources = initialSources.map { it.copy(referenceOwnerIds = it.referenceOwnerIds.toList()) })
            } else saved
        } ?: SessionSettingsStoredState(
            applied = initial.deepCopy().normalized(),
            form = initial.deepCopy().normalized(),
            sources = initialSources.map { it.copy(referenceOwnerIds = it.referenceOwnerIds.toList()) },
            sourceLastUsedAt = initialSources.associate { it.id to it.lastUsedAtEpochMillis }
        )).let { saved ->
            saved.copy(
                sourceLastUsedAt = saved.sources.associate { it.id to it.lastUsedAtEpochMillis } + saved.sourceLastUsedAt
            )
        }
    )
        private set

    init {
        store.save(sessionId, state)
        lastPublishedSnapshot = state.applied.snapshot.deepCopy()
    }

    fun editProfile(transform: (SessionSettingsProfile) -> SessionSettingsProfile) {
        state = state.copy(form = state.form.copy(profile = transform(state.form.profile)))
    }

    fun editGoalPlan(transform: (SessionGoalPlan) -> SessionGoalPlan) {
        state = state.copy(form = state.form.copy(goalPlan = transform(state.form.goalPlan)))
    }

    fun commitTextBoundary() {
        val applied = state.applied
        val proposed = state.form.normalized()
        val protected = protectedDifferences(applied, proposed)
        val safe = proposed.copy(
            profile = proposed.profile.copy(
                learnerRole = applied.profile.learnerRole,
                personality = applied.profile.personality,
                interactionHabits = applied.profile.interactionHabits
            ),
            goalPlan = proposed.goalPlan.copy(primaryGoal = applied.goalPlan.primaryGoal)
        ).normalized()
        state = state.copy(
            applied = if (protected.isEmpty()) proposed else safe,
            form = proposed,
            pendingConfirmation = protected.takeIf { it.isNotEmpty() }
                ?.let(::ProtectedChangeConfirmation),
            errorMessage = null
        )
        persist()
    }

    fun confirmProtectedChanges() {
        if (state.pendingConfirmation == null) return
        val applied = state.form.normalized()
        state = state.copy(applied = applied, form = applied.deepCopy(), pendingConfirmation = null)
        persist()
    }

    fun dismissProtectedChanges() {
        val applied = state.applied.deepCopy()
        state = state.copy(form = applied, pendingConfirmation = null)
        persist()
    }

    fun onApplicationBackgrounded() {
        if (state.pendingConfirmation == null) commitTextBoundary() else persist()
    }

    fun setAvatarVisible(visible: Boolean) = applyImmediate { document ->
        document.copy(profile = document.profile.copy(avatarVisible = visible))
    }

    fun setSpeakingTone(tone: String) = applyImmediate { document ->
        document.copy(strategy = document.strategy.copy(speakingTone = tone))
    }

    fun setStrategy(transform: (ConversationStrategy) -> ConversationStrategy) = applyImmediate { document ->
        document.copy(strategy = transform(document.strategy))
    }

    fun toggleQuickTag(field: String, tag: TagSelectionValue) = applyImmediate { document ->
        val current = document.quickTags[field] ?: TagFieldSelection()
        val values = if (current.values.any { it.tagId == tag.tagId && it.text == tag.text }) {
            current.values.filterNot { it.tagId == tag.tagId && it.text == tag.text }
        } else {
            current.values + tag
        }
        document.copy(quickTags = document.quickTags + (field to TagFieldSelection(values)))
    }

    fun updateWorldTree(transform: (NewSessionConfiguration) -> NewSessionConfiguration) {
        val updated = transform(state.form.snapshot.deepCopy()).deepCopy()
        val form = state.form.copy(snapshot = updated).mirrorSnapshotFields()
        state = state.copy(form = form, applied = form.normalized(), errorMessage = null)
        persist()
    }

    fun editWorldTree(transform: (NewSessionConfiguration) -> NewSessionConfiguration) {
        val updated = transform(state.form.snapshot.deepCopy()).deepCopy()
        state = state.copy(form = state.form.copy(snapshot = updated).mirrorSnapshotFields(), errorMessage = null)
    }

    fun refreshSources(latest: List<SessionSource>) {
        val existing = state.sources.associateBy(SessionSource::id)
        val latestUsage = latest.associate { source ->
            source.id to maxOf(
                source.lastUsedAtEpochMillis,
                state.sourceLastUsedAt[source.id] ?: Long.MIN_VALUE
            )
        }
        state = state.copy(
            sources = latest.map { source ->
                source.copy(
                    displayName = existing[source.id]?.displayName ?: source.displayName,
                    lastUsedAtEpochMillis = latestUsage.getValue(source.id),
                    referenceOwnerIds = source.referenceOwnerIds.toList()
                )
            },
            sourceLastUsedAt = state.sourceLastUsedAt + latestUsage
        )
        persist()
    }

    fun reportError(message: String) {
        state = state.copy(errorMessage = message)
        persist()
    }

    fun markSourceUsed(sourceId: String): Boolean {
        if (source(sourceId) == null) return false
        val usedAt = nowEpochMillis()
        state = state.copy(sources = state.sources.map { source ->
            if (source.id == sourceId) source.copy(lastUsedAtEpochMillis = usedAt) else source
        }, sourceLastUsedAt = state.sourceLastUsedAt + (sourceId to usedAt))
        persist()
        return true
    }

    fun setLearnerImageRef(reference: String?) = applySnapshotFieldImmediate {
        it.copy(learnerImageRef = reference)
    }

    fun setStoryImageRef(reference: String?) = applySnapshotFieldImmediate {
        it.copy(storyImageRef = reference)
    }

    fun visibleSources(
        filter: SourceFilter = SourceFilter.All,
        query: String = ""
    ): List<SessionSource> = state.sources
        .asSequence()
        .filter { source ->
            when (filter) {
                SourceFilter.All -> true
                SourceFilter.Ready -> source.readState == SourceReadState.Ready
                SourceFilter.Processing -> source.readState == SourceReadState.Processing
                SourceFilter.Invalid -> source.readState == SourceReadState.Invalid
            }
        }
        .filter { it.displayName.contains(query.trim(), ignoreCase = true) }
        .sortedByDescending(SessionSource::lastUsedAtEpochMillis)
        .toList()

    fun source(sourceId: String): SessionSource? = state.sources.firstOrNull { it.id == sourceId }

    fun addSource(source: SessionSource) {
        val bound = source.copy(
            currentSessionReferenced = true,
            referenceOwnerIds = (source.referenceOwnerIds + sessionId).distinct(),
            lastUsedAtEpochMillis = nowEpochMillis()
        )
        val ids = (state.applied.snapshot.sourceSelections + bound.id).distinct()
        state = state.copy(
            sources = state.sources.filterNot { it.id == bound.id } + bound,
            applied = state.applied.withSourceSelections(ids),
            form = state.form.withSourceSelections(ids),
            sourceLastUsedAt = state.sourceLastUsedAt + (bound.id to bound.lastUsedAtEpochMillis)
        )
        persist()
        onSourcesChanged()
    }

    fun setSourceAlias(sourceId: String, alias: String): Boolean {
        val normalized = alias.trim()
        if (normalized.isEmpty() || source(sourceId) == null) return false
        state = state.copy(sources = state.sources.map {
            if (it.id == sourceId) it.copy(displayName = normalized) else it
        })
        persist()
        onSourcesChanged()
        return true
    }

    fun unlinkCurrentSession(sourceId: String): Boolean {
        finalizeExpiredSourceAction()
        val source = source(sourceId)?.takeIf { sessionId in it.referenceOwnerIds } ?: return false
        val updated = source.copy(
            currentSessionReferenced = false,
            referenceOwnerIds = source.referenceOwnerIds.filterNot { it == sessionId }
        )
        state = state.copy(
            sources = replaceSource(updated),
            applied = state.applied.withSourceSelections(state.applied.snapshot.sourceSelections - sourceId),
            form = state.form.withSourceSelections(state.form.snapshot.sourceSelections - sourceId),
            sourceUndo = SourceUndo(
                SourceUndoKind.Unlink,
                sourceId,
                source,
                nowEpochMillis() + SessionSettingsUndoWindowMillis,
                state.applied.snapshot.sourceSelections
            ),
            pendingSourceDelete = null
        )
        persist()
        onSourcesChanged()
        return true
    }

    fun requestDeleteSource(sourceId: String) {
        val source = source(sourceId) ?: return
        val impacts = source.referenceOwnerIds.distinct()
        state = state.copy(
            pendingSourceDelete = PendingSourceDelete(
                sourceId = sourceId,
                impactedOwnerIds = impacts,
                deleteEnabled = deleteCapability.available && impacts.none { it != sessionId },
                requiresImpactConfirmation = impacts.any { it != sessionId }
            )
        )
        persist()
    }

    fun acknowledgeDeleteImpact(confirmed: Boolean) {
        val pending = state.pendingSourceDelete ?: return
        state = state.copy(
            pendingSourceDelete = pending.copy(deleteEnabled = deleteCapability.available && confirmed),
            errorMessage = if (deleteCapability.available) null else "当前版本暂不支持删除资料文件。"
        )
        persist()
    }

    fun dismissSourceDelete() {
        state = state.copy(pendingSourceDelete = null)
        persist()
    }

    fun confirmDeleteSource(): Boolean {
        finalizeExpiredSourceAction()
        val pending = state.pendingSourceDelete ?: return false
        if (!deleteCapability.available || !pending.deleteEnabled) {
            state = state.copy(errorMessage = "当前版本暂不支持删除资料文件。")
            persist()
            return false
        }
        val source = source(pending.sourceId) ?: return false
        return when (val result = deleteCapability.delete(source.id)) {
            SourceFileDeleteResult.Deleted -> {
                val ids = state.applied.snapshot.sourceSelections - source.id
                state = state.copy(
                    sources = state.sources.filterNot { it.id == source.id },
                    applied = state.applied.withSourceSelections(ids),
                    form = state.form.withSourceSelections(ids),
                    pendingSourceDelete = null,
                    sourceUndo = null,
                    errorMessage = null,
                    sourceLastUsedAt = state.sourceLastUsedAt - source.id
                )
                persist()
                onSourcesChanged()
                true
            }
            is SourceFileDeleteResult.Failed -> {
                state = state.copy(errorMessage = result.message)
                persist()
                false
            }
        }
    }

    fun undoSourceAction(): Boolean {
        val undo = state.sourceUndo ?: return false
        if (nowEpochMillis() >= undo.expiresAtEpochMillis) {
            finalizeExpiredSourceAction()
            return false
        }
        val usedAt = nowEpochMillis()
        state = state.copy(
            sources = replaceSource(undo.previous.copy(lastUsedAtEpochMillis = usedAt)),
            applied = state.applied.withSourceSelections(undo.previousSelectionIds),
            form = state.form.withSourceSelections(undo.previousSelectionIds),
            sourceUndo = null,
            sourceLastUsedAt = state.sourceLastUsedAt + (undo.sourceId to usedAt)
        )
        persist()
        onSourcesChanged()
        return true
    }

    fun finalizeExpiredSourceAction() {
        val undo = state.sourceUndo ?: return
        if (nowEpochMillis() < undo.expiresAtEpochMillis) return
        state = state.copy(sourceUndo = null)
        persist()
    }

    fun replaceSourceRevision(sourceId: String, replacement: SessionSource): Boolean {
        val old = source(sourceId) ?: return false
        if (replacement.id == old.id) return false
        val oldUpdated = old.copy(
            currentSessionReferenced = false,
            referenceOwnerIds = old.referenceOwnerIds.filterNot { it == sessionId }
        )
        val newRevision = replacement.copy(
            currentSessionReferenced = true,
            referenceOwnerIds = listOf(sessionId),
            previousRevisionId = old.id,
            lastUsedAtEpochMillis = nowEpochMillis()
        )
        state = state.copy(
            sources = state.sources.filterNot { it.id == old.id || it.id == replacement.id } + oldUpdated + newRevision,
            applied = state.applied.withSourceSelections(
                state.applied.snapshot.sourceSelections.map { if (it == old.id) newRevision.id else it }
                    .plus(newRevision.id).distinct()
            ),
            form = state.form.withSourceSelections(
                state.form.snapshot.sourceSelections.map { if (it == old.id) newRevision.id else it }
                    .plus(newRevision.id).distinct()
            ),
            sourceLastUsedAt = state.sourceLastUsedAt + (newRevision.id to newRevision.lastUsedAtEpochMillis)
        )
        persist()
        onSourcesChanged()
        return true
    }

    fun reselectInvalidSource(sourceId: String, replacement: SessionSource): Boolean =
        replaceSourceRevision(sourceId, replacement)

    private fun applyImmediate(transform: (SessionSettingsDocument) -> SessionSettingsDocument) {
        val applied = transform(state.applied).normalized()
        val form = transform(state.form).normalized()
        state = state.copy(applied = applied, form = form, errorMessage = null)
        persist()
    }

    private fun applySnapshotFieldImmediate(transform: (NewSessionConfiguration) -> NewSessionConfiguration) {
        state = state.copy(
            applied = state.applied.copy(snapshot = transform(state.applied.snapshot.deepCopy()).deepCopy()),
            form = state.form.copy(snapshot = transform(state.form.snapshot.deepCopy()).deepCopy()),
            errorMessage = null
        )
        persist()
    }

    private fun replaceSource(source: SessionSource): List<SessionSource> = state.sources.map {
        if (it.id == source.id) source else it
    }

    private fun persist() {
        store.save(sessionId, state)
        val snapshot = state.applied.snapshot.deepCopy()
        if (snapshot != lastPublishedSnapshot) {
            lastPublishedSnapshot = snapshot
            onSnapshotApplied(snapshot)
        }
    }
}

private fun SessionSettingsDocument.mirrorSnapshotFields(): SessionSettingsDocument = copy(
    profile = profile.copy(
        title = snapshot.title,
        learnerDisplayName = snapshot.learnerDisplayName,
        learnerRole = snapshot.learnerRole,
        avatarVisible = snapshot.avatarVisible,
        personality = snapshot.learnerProfile,
        interactionHabits = snapshot.dialogueStrategy
    ),
    goalPlan = goalPlan.copy(
        primaryGoal = snapshot.goal,
        deadline = snapshot.deadline,
        learningScope = snapshot.learningScope,
        modules = snapshot.modules,
        stageMilestones = snapshot.stageMilestones,
        weeklyPlan = snapshot.plan.ifBlank { goalPlan.weeklyPlan },
        currentState = snapshot.currentState
    ),
    strategy = ConversationStrategy(
        feedbackIntensity = snapshot.feedbackIntensity,
        probingIntensity = snapshot.probingIntensity,
        scaffoldingIntensity = snapshot.scaffoldingIntensity,
        correctionPersistence = snapshot.correctionPersistence,
        reviewFrequency = snapshot.reviewFrequency,
        speakingTone = snapshot.speakingTone
    )
)

private fun SessionSettingsDocument.normalized(): SessionSettingsDocument = copy(
    snapshot = snapshot.copy(
        title = profile.title,
        learnerDisplayName = profile.learnerDisplayName,
        learnerRole = profile.learnerRole,
        avatarVisible = profile.avatarVisible,
        learnerProfile = profile.personality,
        goal = goalPlan.primaryGoal,
        plan = goalPlan.weeklyPlan,
        deadline = goalPlan.deadline,
        learningScope = goalPlan.learningScope,
        modules = goalPlan.modules,
        stageMilestones = goalPlan.stageMilestones,
        currentState = goalPlan.currentState,
        dialogueStrategy = profile.interactionHabits,
        feedbackIntensity = strategy.feedbackIntensity,
        probingIntensity = strategy.probingIntensity,
        scaffoldingIntensity = strategy.scaffoldingIntensity,
        correctionPersistence = strategy.correctionPersistence,
        reviewFrequency = strategy.reviewFrequency,
        speakingTone = strategy.speakingTone,
        quickTags = quickTags.mapValues { it.value.deepCopy() }
    ).deepCopy(),
    quickTags = quickTags.mapValues { it.value.deepCopy() }
)

private fun SessionSettingsDocument.withSourceSelections(ids: List<String>): SessionSettingsDocument = copy(
    snapshot = snapshot.copy(sourceSelections = ids.toList()).deepCopy()
)

private fun protectedDifferences(
    before: SessionSettingsDocument,
    after: SessionSettingsDocument
): List<ConfigurationDifference> = buildList {
    fun difference(field: String, old: String, new: String) {
        if (old != new) add(ConfigurationDifference(field, old, new))
    }
    difference("学习者角色", before.profile.learnerRole, after.profile.learnerRole)
    difference("主要目标", before.goalPlan.primaryGoal, after.goalPlan.primaryGoal)
    difference("人格", before.profile.personality, after.profile.personality)
    difference("互动习惯", before.profile.interactionHabits, after.profile.interactionHabits)
}
