package com.reversetutor.feature.chat

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class NewSessionHubTab(val label: String) {
    BuiltIn("内置预设"),
    Favorites("收藏"),
    Custom("自定义")
}

enum class NewSessionSection(val label: String) {
    Basic("基本资料"),
    Goals("目标与计划"),
    Dialogue("对话策略"),
    WorldTree("世界树"),
    Sources("资料"),
    CustomFields("自定义栏目")
}

data class NewSessionConfiguration(
    val title: String = "",
    val learnerRole: String = "",
    val learnerProfile: String = "",
    val learnerDisplayName: String = "学习者",
    val avatarVisible: Boolean = true,
    val goal: String = "",
    val plan: String = "",
    val deadline: String = "未设置",
    val learningScope: String = "未设置",
    val modules: String = "未设置",
    val stageMilestones: String = "未设置",
    val currentState: String = "未设置",
    val dialogueStrategy: String = "",
    val feedbackIntensity: Int = 3,
    val probingIntensity: Int = 3,
    val scaffoldingIntensity: Int = 3,
    val correctionPersistence: String = "适中",
    val reviewFrequency: String = "每周",
    val speakingTone: String = "自然",
    val story: String = "",
    val sourceSelections: List<String> = emptyList(),
    val customFields: Map<String, String> = emptyMap(),
    val customColumns: List<CustomColumn> = emptyList(),
    val openingMessage: String = "准备好后，请开始讲给我听吧。",
    val learnerImageRef: String? = null,
    val storyImageRef: String? = null,
    val builtInPresetId: String? = null,
    val quickTags: Map<String, TagFieldSelection> = emptyMap()
) {
    fun validationErrors(): List<String> = buildList {
        if (title.isBlank()) add("请填写会话名称。")
        if (learnerRole.isBlank()) add("请填写学习者角色。")
    }

    val completionPercent: Int
        get() {
            val completed = listOf(
                title.isNotBlank(), learnerRole.isNotBlank(), learnerProfile.isNotBlank(),
                goal.isNotBlank(), plan.isNotBlank(), dialogueStrategy.isNotBlank(),
                story.isNotBlank(), sourceSelections.isNotEmpty(), effectiveCustomColumns().isNotEmpty(),
                openingMessage.isNotBlank()
            ).count { it }
            return completed * 10
        }

    fun sectionSummary(section: NewSessionSection): String = when (section) {
        NewSessionSection.Basic -> listOf(title, learnerRole, learnerProfile, openingMessage)
            .count { it.isNotBlank() }.let { "$it / 4 项已填写" }
        NewSessionSection.Goals -> listOf(goal, plan).count { it.isNotBlank() }
            .let { "$it / 2 项已填写" }
        NewSessionSection.Dialogue -> dialogueStrategy.ifBlank { "未填写" }
            .replace(Regex("\\s+"), " ").take(36)
        NewSessionSection.WorldTree -> story.ifBlank { "未填写" }
            .replace(Regex("\\s+"), " ").take(36)
        NewSessionSection.Sources -> "已选择 ${sourceSelections.size} 项"
        NewSessionSection.CustomFields -> "${effectiveCustomColumns().size} 个栏目"
    }

    fun toCoreDraft(): NewSessionDraft = NewSessionDraft(
        title = title,
        role = learnerRole,
        goal = goal,
        profileText = buildString {
            append(learnerProfile.ifBlank { "未填写" })
            append("\nPlan: ").append(plan.ifBlank { "未填写" })
            append("\nDialogue: ").append(dialogueStrategy.ifBlank { "未填写" })
            append("\nStory: ").append(story.ifBlank { "未填写" })
            append("\nSources: ").append(sourceSelections.joinToString().ifBlank { "未选择" })
            if (effectiveCustomColumns().isNotEmpty()) {
                append("\nCustom: ")
                append(effectiveCustomColumns().joinToString { column ->
                    buildString {
                        append(column.name).append('=').append(column.content)
                        val tags = column.tags.values.map(TagSelectionValue::text)
                        if (tags.isNotEmpty()) append(" [").append(tags.joinToString()).append(']')
                    }
                })
            }
        },
        templateId = builtInPresetId,
        sourceHandoffRequested = sourceSelections.isNotEmpty()
    )

    companion object {
        fun fromPreset(preset: FormalLearningPreset): NewSessionConfiguration =
            NewSessionConfiguration(
                title = preset.title,
                learnerRole = "AI 学生 ${preset.learnerName}：${preset.learnerProfile}",
                learnerProfile = preset.learnerProfile,
                learnerDisplayName = preset.learnerName,
                goal = preset.goal,
                plan = preset.schedule,
                dialogueStrategy = "用户作为老师负责讲解，学习者持续追问依据和推导。",
                story = "${preset.episodeTitle}：${preset.episodeBody}",
                sourceSelections = List(preset.connectedSources) { index ->
                    "${preset.sourceTitle} ${index + 1}"
                },
                openingMessage = "我是${preset.learnerName}。${preset.episodeBody}",
                learnerImageRef = LearnerAvatarReference.PackagedDrawable(preset.avatarRes).persistedValue,
                storyImageRef = preset.storyRes.toString(),
                builtInPresetId = preset.id
            )
    }

    fun effectiveCustomColumns(): List<CustomColumn> =
        if (customColumns.isNotEmpty()) customColumns.map(CustomColumn::deepCopy)
        else customFields.entries.mapIndexed { index, entry ->
            CustomColumn("legacy-column-$index", entry.key, entry.value)
        }
}

data class NewSessionDraftRecord(
    val id: String,
    val configuration: NewSessionConfiguration,
    val updatedAtEpochMillis: Long,
    val favoriteId: String? = null,
    val originBuiltInPresetId: String? = null,
    val lastSection: NewSessionSection? = null,
    val scrollPositions: Map<String, EditorScrollPosition> = emptyMap()
)

data class EditorScrollPosition(val index: Int = 0, val offset: Int = 0)

data class NewSessionFavorite(
    val id: String,
    val name: String,
    val configuration: NewSessionConfiguration,
    val updatedAtEpochMillis: Long,
    val originBuiltInPresetId: String? = null
)

data class ConfigurationDifference(
    val field: String,
    val before: String,
    val after: String
)

data class RandomDifferencePreview(
    val before: NewSessionConfiguration,
    val after: NewSessionConfiguration,
    val differences: List<ConfigurationDifference>
)

data class FavoriteUpdatePreview(
    val favorite: NewSessionFavorite,
    val replacement: NewSessionConfiguration,
    val differences: List<ConfigurationDifference>
)

data class NewSessionCreated(
    val session: SessionListItem,
    val learnerRole: String,
    val openingMessage: String
)

data class NewSessionCreateRequest(
    val attemptId: String,
    val draftId: String,
    val snapshot: NewSessionConfiguration
)

fun interface NewSessionCreatePort {
    suspend fun createSession(request: NewSessionCreateRequest): NewSessionCreated
}

interface NewSessionPersistence {
    fun loadDrafts(): List<NewSessionDraftRecord>
    fun replaceDrafts(drafts: List<NewSessionDraftRecord>)
    fun loadFavorites(): List<NewSessionFavorite>
    fun replaceFavorites(favorites: List<NewSessionFavorite>)
    fun promoteDraft(draftId: String, sessionId: String, snapshot: NewSessionConfiguration)
    fun loadSessionSnapshot(sessionId: String): NewSessionConfiguration?
    fun saveSessionSnapshot(sessionId: String, snapshot: NewSessionConfiguration) = Unit
}

fun interface NewSessionRandomizer {
    fun next(current: NewSessionConfiguration): NewSessionConfiguration
}

data class NewSessionLifecycleState(
    val tab: NewSessionHubTab = NewSessionHubTab.BuiltIn,
    val drafts: List<NewSessionDraftRecord> = emptyList(),
    val favorites: List<NewSessionFavorite> = emptyList(),
    val currentDraft: NewSessionDraftRecord? = null,
    val selectedPresetId: String? = null,
    val selectedSection: NewSessionSection? = null,
    val randomPreview: RandomDifferencePreview? = null,
    val favoriteUpdatePreview: FavoriteUpdatePreview? = null,
    val canUndoRandom: Boolean = false,
    val creating: Boolean = false,
    val createError: String? = null,
    val persistenceError: String? = null
)

sealed interface CreateSessionOutcome {
    data class Success(val created: NewSessionCreated) : CreateSessionOutcome
    data class Invalid(val errors: List<String>) : CreateSessionOutcome
    data object Duplicate : CreateSessionOutcome
    data class Failure(val message: String) : CreateSessionOutcome
}

class NewSessionLifecycleCoordinator(
    private val persistence: NewSessionPersistence,
    private val createPort: NewSessionCreatePort,
    private val randomizer: NewSessionRandomizer = DefaultNewSessionRandomizer,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { nowEpochMillis().toString() }
) {
    private val createMutex = Mutex()
    private var undoRandomValues: RandomUndoValues? = null
    private var createAttemptId: String? = null

    var state: NewSessionLifecycleState = NewSessionLifecycleState()
        private set

    fun load() {
        state = state.copy(
            drafts = persistence.loadDrafts().sortedByDescending { it.updatedAtEpochMillis },
            favorites = persistence.loadFavorites().sortedByDescending { it.updatedAtEpochMillis }
        )
    }

    fun selectTab(tab: NewSessionHubTab): Boolean {
        if (state.tab == NewSessionHubTab.Custom && tab != NewSessionHubTab.Custom && !saveBoundary()) {
            return false
        }
        if (tab == NewSessionHubTab.Custom && state.currentDraft == null && !startBlankDraft()) {
            return false
        }
        state = state.copy(tab = tab, selectedPresetId = null, selectedSection = null)
        return true
    }

    fun openPresetDetail(presetId: String) {
        state = state.copy(selectedPresetId = presetId, selectedSection = null)
    }

    fun closePresetDetail() {
        state = state.copy(selectedPresetId = null)
    }

    fun useBuiltInPreset(preset: FormalLearningPreset) {
        val record = NewSessionDraftRecord(
            id = "draft-${idFactory()}",
            configuration = NewSessionConfiguration.fromPreset(preset),
            updatedAtEpochMillis = nowEpochMillis(),
            originBuiltInPresetId = preset.id
        )
        state = state.copy(
            tab = NewSessionHubTab.Custom,
            currentDraft = record,
            selectedPresetId = null,
            selectedSection = null,
            createError = null,
            persistenceError = null
        )
        createAttemptId = null
    }

    fun startBlankDraft(): Boolean {
        if (state.currentDraft != null && !saveBoundary()) return false
        val record = NewSessionDraftRecord(
            id = "draft-${idFactory()}",
            configuration = NewSessionConfiguration(),
            updatedAtEpochMillis = nowEpochMillis()
        )
        state = state.copy(tab = NewSessionHubTab.Custom, currentDraft = record, selectedSection = null)
        createAttemptId = null
        return true
    }

    fun updateConfiguration(transform: (NewSessionConfiguration) -> NewSessionConfiguration) {
        val draft = state.currentDraft ?: return
        val transformed = transform(draft.configuration)
        if (transformed == draft.configuration) return
        val updatedDraft = draft.withConfigurationEdit(transformed)
        state = state.copy(
            currentDraft = updatedDraft,
            createError = null,
            persistenceError = null
        )
        createAttemptId = null
    }

    fun openSection(section: NewSessionSection, currentPosition: EditorScrollPosition? = null): Boolean {
        if (!saveBoundary(position = currentPosition)) return false
        state = state.copy(selectedSection = section)
        return true
    }

    fun closeSection(position: EditorScrollPosition? = null): Boolean {
        if (!saveBoundary(position = position)) return false
        state = state.copy(selectedSection = null)
        return saveBoundary(section = null)
    }

    fun saveBoundary(
        section: NewSessionSection? = state.selectedSection,
        position: EditorScrollPosition? = null,
        protectedDraftIds: Set<String> = emptySet()
    ): Boolean {
        val draft = state.currentDraft ?: return true
        val positionKey = section?.name ?: "root"
        val positioned = draft.copy(
            updatedAtEpochMillis = nowEpochMillis(),
            lastSection = section,
            scrollPositions = if (position == null) draft.scrollPositions else
                draft.scrollPositions + (positionKey to position)
        )
        val others = state.drafts.filterNot { it.id == positioned.id }
        val capacityResult = enforceDraftCapacity(
            candidates = others + positioned,
            protectedDraftIds = protectedDraftIds + positioned.id
        )
        if (capacityResult == null) {
            state = state.copy(persistenceError = "草稿箱已满，且没有可移除的未收藏草稿。")
            return false
        }
        persistence.replaceDrafts(capacityResult)
        state = state.copy(
            drafts = capacityResult.sortedByDescending { it.updatedAtEpochMillis },
            currentDraft = positioned,
            persistenceError = null
        )
        return true
    }

    fun restoreDraft(draftId: String): Boolean {
        state.drafts.firstOrNull { it.id == draftId } ?: return false
        if (!saveBoundary(protectedDraftIds = setOf(draftId))) return false
        val destination = state.drafts.firstOrNull { it.id == draftId } ?: return false
        state = state.copy(
            tab = NewSessionHubTab.Custom,
            currentDraft = destination,
            selectedSection = destination.lastSection,
            createError = null,
            persistenceError = null
        )
        createAttemptId = null
        return true
    }

    fun renameDraft(draftId: String, name: String): Boolean {
        val normalized = name.trim()
        if (normalized.isEmpty()) return false
        val updated = state.drafts.map { record ->
            if (record.id == draftId) {
                record.withConfigurationEdit(record.configuration.copy(title = normalized))
                    .copy(updatedAtEpochMillis = nowEpochMillis())
            } else record
        }
        if (updated == state.drafts) return false
        persistence.replaceDrafts(updated)
        state = state.copy(
            drafts = updated.sortedByDescending { it.updatedAtEpochMillis },
            currentDraft = state.currentDraft?.let { current ->
                updated.firstOrNull { it.id == current.id } ?: current
            }
        )
        return true
    }

    fun copyDraft(draftId: String): Boolean {
        val source = state.drafts.firstOrNull { it.id == draftId } ?: return false
        val copy = source.copy(
            id = "draft-${idFactory()}",
            configuration = source.configuration.copy(title = "${source.configuration.title} · 副本"),
            updatedAtEpochMillis = nowEpochMillis(),
            favoriteId = null,
            originBuiltInPresetId = null
        )
        val capacityResult = enforceDraftCapacity(state.drafts + copy, setOf(copy.id)) ?: return false
        persistence.replaceDrafts(capacityResult)
        state = state.copy(drafts = capacityResult.sortedByDescending { it.updatedAtEpochMillis })
        return true
    }

    fun deleteDraft(draftId: String) {
        val updated = state.drafts.filterNot { it.id == draftId }
        persistence.replaceDrafts(updated)
        val deletingActive = state.currentDraft?.id == draftId
        val replacement = if (deletingActive) {
            updated.maxByOrNull { it.updatedAtEpochMillis } ?: NewSessionDraftRecord(
                id = "draft-${idFactory()}",
                configuration = NewSessionConfiguration(),
                updatedAtEpochMillis = nowEpochMillis()
            )
        } else state.currentDraft
        state = state.copy(
            drafts = updated,
            currentDraft = replacement,
            selectedSection = if (deletingActive) replacement?.lastSection else state.selectedSection
        )
        if (deletingActive) createAttemptId = null
    }

    fun toggleBuiltInFavorite(preset: FormalLearningPreset) {
        val existing = state.favorites.firstOrNull { it.originBuiltInPresetId == preset.id }
        if (existing != null) {
            removeFavorite(existing.id)
            return
        }
        val favorite = NewSessionFavorite(
            id = "favorite-${idFactory()}",
            name = preset.title,
            configuration = NewSessionConfiguration.fromPreset(preset),
            updatedAtEpochMillis = nowEpochMillis(),
            originBuiltInPresetId = preset.id
        )
        replaceFavorites(state.favorites + favorite)
    }

    fun useFavorite(favoriteId: String): Boolean {
        val favorite = state.favorites.firstOrNull { it.id == favoriteId } ?: return false
        if (state.currentDraft != null && !saveBoundary()) return false
        val draft = NewSessionDraftRecord(
            id = "draft-${idFactory()}",
            configuration = favorite.configuration.copy(),
            updatedAtEpochMillis = nowEpochMillis(),
            favoriteId = favorite.id
        )
        state = state.copy(tab = NewSessionHubTab.Custom, currentDraft = draft, selectedSection = null)
        createAttemptId = null
        return true
    }

    fun requestFavoriteCurrent() {
        val draft = state.currentDraft ?: return
        val linked = draft.favoriteId?.let { id -> state.favorites.firstOrNull { it.id == id } }
        if (linked == null) {
            val favorite = NewSessionFavorite(
                id = "favorite-${idFactory()}",
                name = draft.configuration.title.ifBlank { "未命名配置" },
                configuration = draft.configuration.copy(),
                updatedAtEpochMillis = nowEpochMillis()
            )
            replaceFavorites(state.favorites + favorite)
            val linkedDrafts = state.drafts.map {
                if (it.id == draft.id) it.copy(favoriteId = favorite.id) else it
            }
            persistence.replaceDrafts(linkedDrafts)
            state = state.copy(
                drafts = linkedDrafts,
                currentDraft = draft.copy(favoriteId = favorite.id)
            )
            return
        }
        val differences = configurationDifferences(linked.configuration, draft.configuration)
        if (differences.isNotEmpty()) {
            state = state.copy(
                favoriteUpdatePreview = FavoriteUpdatePreview(linked, draft.configuration.copy(), differences)
            )
        }
    }

    fun confirmFavoriteUpdate() {
        val preview = state.favoriteUpdatePreview ?: return
        replaceFavorites(state.favorites.map {
            if (it.id == preview.favorite.id) it.copy(
                name = preview.replacement.title.ifBlank { it.name },
                configuration = preview.replacement.copy(),
                updatedAtEpochMillis = nowEpochMillis()
            ) else it
        })
        state = state.copy(favoriteUpdatePreview = null)
    }

    fun dismissFavoriteUpdate() {
        state = state.copy(favoriteUpdatePreview = null)
    }

    fun removeFavorite(favoriteId: String) {
        replaceFavorites(state.favorites.filterNot { it.id == favoriteId })
        val drafts = state.drafts.map { if (it.favoriteId == favoriteId) it.copy(favoriteId = null) else it }
        persistence.replaceDrafts(drafts)
        state = state.copy(
            drafts = drafts,
            currentDraft = state.currentDraft?.let {
                if (it.favoriteId == favoriteId) it.copy(favoriteId = null) else it
            }
        )
    }

    fun previewRandom() {
        val current = state.currentDraft?.configuration ?: return
        val proposed = randomizer.next(current).copy(
            title = current.title,
            learnerProfile = current.learnerProfile,
            dialogueStrategy = current.dialogueStrategy,
            story = current.story,
            sourceSelections = current.sourceSelections,
            customFields = current.customFields,
            customColumns = current.customColumns.map(CustomColumn::deepCopy),
            openingMessage = current.openingMessage,
            learnerImageRef = current.learnerImageRef,
            storyImageRef = current.storyImageRef,
            builtInPresetId = current.builtInPresetId
        )
        state = state.copy(
            randomPreview = RandomDifferencePreview(
                before = current,
                after = proposed,
                differences = configurationDifferences(current, proposed)
                    .filter { it.field in setOf("学习者角色", "主要目标", "学习计划") }
            )
        )
    }

    fun applyRandom() {
        val preview = state.randomPreview ?: return
        undoRandomValues = RandomUndoValues(
            learnerRole = preview.before.learnerRole,
            goal = preview.before.goal,
            plan = preview.before.plan
        )
        val draft = state.currentDraft ?: return
        state = state.copy(
            currentDraft = draft.withConfigurationEdit(preview.after.copy()),
            randomPreview = null,
            canUndoRandom = true
        )
        createAttemptId = null
    }

    fun dismissRandom() {
        state = state.copy(randomPreview = null)
    }

    fun undoRandom() {
        val previous = undoRandomValues ?: return
        val draft = state.currentDraft ?: return
        state = state.copy(
            currentDraft = draft.copy(
                configuration = draft.configuration.copy(
                    learnerRole = previous.learnerRole,
                    goal = previous.goal,
                    plan = previous.plan
                )
            ),
            canUndoRandom = false
        )
        undoRandomValues = null
        createAttemptId = null
    }

    suspend fun createSession(): CreateSessionOutcome {
        val request = createMutex.withLock {
            if (state.creating) return CreateSessionOutcome.Duplicate
            val draft = state.currentDraft ?: return CreateSessionOutcome.Invalid(listOf("没有可创建的配置。"))
            val errors = draft.configuration.validationErrors()
            if (errors.isNotEmpty()) return CreateSessionOutcome.Invalid(errors)
            val attempt = createAttemptId ?: "create-${idFactory()}".also { createAttemptId = it }
            state = state.copy(creating = true, createError = null)
            NewSessionCreateRequest(attempt, draft.id, draft.configuration.deepCopy())
        }
        return runCatching { createPort.createSession(request) }
            .fold(
                onSuccess = { created ->
                    runCatching {
                        persistence.promoteDraft(request.draftId, created.session.id, request.snapshot.deepCopy())
                    }.fold(
                        onSuccess = {
                            val remainingDrafts = state.drafts.filterNot { it.id == request.draftId }
                            state = state.copy(
                                drafts = remainingDrafts,
                                currentDraft = null,
                                creating = false,
                                createError = null
                            )
                            createAttemptId = null
                            CreateSessionOutcome.Success(created)
                        },
                        onFailure = {
                            val message = "会话已准备好，但本地快照保存失败。请重试。"
                            state = state.copy(creating = false, createError = message)
                            CreateSessionOutcome.Failure(message)
                        }
                    )
                },
                onFailure = {
                    val message = "暂时无法创建会话，所有编辑均已保留。请重试。"
                    state = state.copy(creating = false, createError = message)
                    CreateSessionOutcome.Failure(message)
                }
            )
    }

    private fun replaceFavorites(favorites: List<NewSessionFavorite>) {
        persistence.replaceFavorites(favorites)
        state = state.copy(favorites = favorites.sortedByDescending { it.updatedAtEpochMillis })
    }
}

private data class RandomUndoValues(
    val learnerRole: String,
    val goal: String,
    val plan: String
)

private fun NewSessionDraftRecord.withConfigurationEdit(
    updated: NewSessionConfiguration
): NewSessionDraftRecord {
    if (originBuiltInPresetId == null) return copy(configuration = updated)
    return copy(
        configuration = updated.copy(
            title = "${configuration.title} · 副本",
            builtInPresetId = null
        ),
        favoriteId = null,
        originBuiltInPresetId = null
    )
}

private fun enforceDraftCapacity(
    candidates: List<NewSessionDraftRecord>,
    protectedDraftIds: Set<String>
): List<NewSessionDraftRecord>? {
    if (candidates.size <= 20) return candidates
    val evictable = candidates
        .filter { it.id !in protectedDraftIds && it.favoriteId == null }
        .minByOrNull { it.updatedAtEpochMillis }
        ?: return null
    return candidates.filterNot { it.id == evictable.id }
}

fun configurationDifferences(
    before: NewSessionConfiguration,
    after: NewSessionConfiguration
): List<ConfigurationDifference> = buildList {
    fun add(label: String, old: String, new: String) {
        if (old != new) add(ConfigurationDifference(label, old, new))
    }
    add("会话名称", before.title, after.title)
    add("学习者角色", before.learnerRole, after.learnerRole)
    add("学习者画像", before.learnerProfile, after.learnerProfile)
    add("主要目标", before.goal, after.goal)
    add("学习计划", before.plan, after.plan)
    add("对话策略", before.dialogueStrategy, after.dialogueStrategy)
    add("世界树", before.story, after.story)
    add("资料", before.sourceSelections.joinToString(), after.sourceSelections.joinToString())
    add(
        "自定义栏目",
        before.effectiveCustomColumns().joinToString { "${it.name}=${it.content}:${it.tags.values.map(TagSelectionValue::text)}" },
        after.effectiveCustomColumns().joinToString { "${it.name}=${it.content}:${it.tags.values.map(TagSelectionValue::text)}" }
    )
    add("开场消息", before.openingMessage, after.openingMessage)
    add("学习者图片", before.learnerImageRef.orEmpty(), after.learnerImageRef.orEmpty())
    add("故事图片", before.storyImageRef.orEmpty(), after.storyImageRef.orEmpty())
}

object DefaultNewSessionRandomizer : NewSessionRandomizer {
    private val roles = listOf("好奇、会追问证据的初学者", "谨慎、容易漏步骤的学习者", "喜欢举例验证的实践型学习者")
    private val goals = listOf("把核心概念讲成可以复述的方法", "通过反例找出理解中的缺口", "完成一次结构清晰的教学演练")
    private val plans = listOf("先诊断基础，再分三轮讲解与复盘", "用 20 分钟完成概念、例题和迁移练习", "每次聚焦一个问题，并记录下一步")

    override fun next(current: NewSessionConfiguration): NewSessionConfiguration {
        val seed = (current.learnerRole + current.goal + current.plan).hashCode().toUInt().toLong()
        val index = (seed % roles.size).toInt()
        return current.copy(
            learnerRole = roles[index],
            goal = goals[(index + 1) % goals.size],
            plan = plans[(index + 2) % plans.size]
        )
    }
}
