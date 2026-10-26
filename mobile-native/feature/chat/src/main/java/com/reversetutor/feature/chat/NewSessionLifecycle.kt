package com.reversetutor.feature.chat

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class NewSessionHubTab(val label: String) {
    BuiltIn("内置预设"),
    Favorites("收藏")
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
    val stagnationIntervention: String = "自动",
    val story: String = "",
    val sourceSelections: List<String> = emptyList(),
    val customFields: Map<String, String> = emptyMap(),
    val customColumns: List<CustomColumn> = emptyList(),
    val openingMessage: String = "准备好后，请开始讲给我听吧。",
    val learnerImageRef: String? = null,
    val storyImageRef: String? = null,
    val builtInPresetId: String? = null,
    val quickTags: Map<String, TagFieldSelection> = emptyMap(),
    /** 人物性格（R84）：创建链路「用户目标 → 人物性格 → 教学方式」的中间环。 */
    val persona: String = "",
    /** 有序学习路径（R86）：教材目录或 LLM 分解统一落成，创建后冻结，算法按掌握度推进。 */
    val learningPath: List<String> = emptyList()
) {
    fun validationErrors(): List<String> = buildList {
        if (title.isBlank()) add("请填写会话名称。")
        if (learnerRole.isBlank()) add("请填写学习者角色。")
    }

    val completionPercent: Int
        get() {
            val completed = listOf(
                title.isNotBlank(), learnerRole.isNotBlank(), learnerProfile.isNotBlank(),
                persona.isNotBlank(), goal.isNotBlank(), plan.isNotBlank(), dialogueStrategy.isNotBlank(),
                story.isNotBlank(), sourceSelections.isNotEmpty(), effectiveCustomColumns().isNotEmpty(),
                openingMessage.isNotBlank()
            ).count { it }
            return completed * 100 / 11
        }

    fun toCoreDraft(): NewSessionDraft = NewSessionDraft(
        title = title,
        role = learnerRole,
        goal = goal,
        profileText = buildString {
            append(learnerProfile.ifBlank { "未填写" })
            append("\nPersona: ").append(persona.ifBlank { "未填写" })
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
                // 开场消息必须是「学生说话」（2026-10-15 真机反馈）：不再拿写给老师的
                // 第二人称剧情简介（episodeBody）拼「我是X。」旁白；openingLine 缺席时
                // 才回退旧行为，保证自定义预设不因缺字段而空开场。
                openingMessage = preset.openingLine.ifBlank { "我是${preset.learnerName}。${preset.episodeBody}" },
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
    val originBuiltInPresetId: String? = null
)

data class NewSessionFavorite(
    val id: String,
    val name: String,
    val configuration: NewSessionConfiguration,
    val updatedAtEpochMillis: Long,
    val originBuiltInPresetId: String? = null
)

/**
 * 一处配置差异（字段名 / 改前 / 改后）。
 *
 * 2026-10-23 方案B：创建页的随机预览与收藏更新预览已删除，
 * 但会话设置页的「改设置前先确认哪些字段被动过」仍在用这个类型。
 */
data class ConfigurationDifference(
    val field: String,
    val before: String,
    val after: String
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

data class NewSessionLifecycleState(
    val tab: NewSessionHubTab = NewSessionHubTab.BuiltIn,
    val drafts: List<NewSessionDraftRecord> = emptyList(),
    val favorites: List<NewSessionFavorite> = emptyList(),
    val currentDraft: NewSessionDraftRecord? = null,
    val selectedPresetId: String? = null,
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
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { nowEpochMillis().toString() }
) {
    private val createMutex = Mutex()
    private var createAttemptId: String? = null

    var state: NewSessionLifecycleState = NewSessionLifecycleState()
        private set

    /** 会话创建只剩「内置预设 / 收藏」两个取材入口，选中即交给对话式创建。 */
    fun selectTab(tab: NewSessionHubTab) {
        state = state.copy(tab = tab, selectedPresetId = null)
    }

    fun load() {
        state = state.copy(
            drafts = persistence.loadDrafts().sortedByDescending { it.updatedAtEpochMillis },
            favorites = persistence.loadFavorites().sortedByDescending { it.updatedAtEpochMillis }
        )
    }

    fun openPresetDetail(presetId: String) {
        state = state.copy(selectedPresetId = presetId)
    }

    fun closePresetDetail() {
        state = state.copy(selectedPresetId = null)
    }

    fun startBlankDraft(): Boolean {
        if (state.currentDraft != null && !saveBoundary()) return false
        val record = NewSessionDraftRecord(
            id = "draft-${idFactory()}",
            configuration = NewSessionConfiguration(),
            updatedAtEpochMillis = nowEpochMillis()
        )
        state = state.copy(currentDraft = record)
        createAttemptId = null
        return true
    }

    fun startPrefilledDraft(prefill: NewSessionPrefillRequest): Boolean {
        if (state.currentDraft != null && !saveBoundary()) return false
        val record = NewSessionDraftRecord(
            id = "draft-${prefill.requestId}",
            configuration = prefill.configuration.deepCopy(),
            updatedAtEpochMillis = nowEpochMillis()
        )
        state = state.copy(
            currentDraft = record,
            selectedPresetId = null,
            createError = null,
            persistenceError = null
        )
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

    fun saveBoundary(protectedDraftIds: Set<String> = emptySet()): Boolean {
        val draft = state.currentDraft ?: return true
        val positioned = draft.copy(updatedAtEpochMillis = nowEpochMillis())
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
            currentDraft = destination,
            createError = null,
            persistenceError = null
        )
        createAttemptId = null
        return true
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
                            CreateSessionOutcome.Success(
                                created.copy(
                                    session = created.session.copy(
                                        challengeProvenance = request.snapshot.challengeSessionProvenance()
                                    )
                                )
                            )
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
