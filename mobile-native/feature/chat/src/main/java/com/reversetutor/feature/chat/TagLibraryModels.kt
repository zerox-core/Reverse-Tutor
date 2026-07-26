package com.reversetutor.feature.chat

enum class TagOrigin { BuiltIn, Custom }

const val UngroupedTagRangeId = "__ungrouped__"
const val CollapsedTagVisibleCapacity = 8

data class TagSwatch(val argb: Long) {
    val saturation: Float
        get() {
            val red = ((argb shr 16) and 0xFF).toFloat() / 255f
            val green = ((argb shr 8) and 0xFF).toFloat() / 255f
            val blue = (argb and 0xFF).toFloat() / 255f
            val maximum = maxOf(red, green, blue)
            val minimum = minOf(red, green, blue)
            return if (maximum == 0f) 0f else (maximum - minimum) / maximum
        }
}

object TagColorPalette {
    val swatches: List<TagSwatch> = listOf(
        TagSwatch(0xFFE7A6A6),
        TagSwatch(0xFFE8B58F),
        TagSwatch(0xFFE4C989),
        TagSwatch(0xFFD4D99A),
        TagSwatch(0xFFB8D8A8),
        TagSwatch(0xFFA8D8C8),
        TagSwatch(0xFFA7D2DD),
        TagSwatch(0xFFAEC3E5),
        TagSwatch(0xFFB8B6E2),
        TagSwatch(0xFFD0B3DF),
        TagSwatch(0xFFE2B2CE),
        TagSwatch(0xFFD1C2B5)
    )
}

data class QuickTag(
    val id: String,
    val name: String,
    val origin: TagOrigin
)

data class TagGroup(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val origin: TagOrigin,
    val tagIds: List<String> = emptyList()
)

data class TagLibrarySnapshot(
    val groups: List<TagGroup>,
    val tags: Map<String, QuickTag>,
    val ungroupedTagIds: List<String> = emptyList()
) {
    fun group(id: String?): TagGroup? = groups.firstOrNull { it.id == id }

    fun orderedTags(): List<QuickTag> = buildList {
        groups.forEach { group -> group.tagIds.mapNotNullTo(this) { tags[it] } }
        ungroupedTagIds.mapNotNullTo(this) { tags[it] }
    }

    fun groupIdFor(tagId: String): String? = groups.firstOrNull { tagId in it.tagIds }?.id

    fun deepCopy(): TagLibrarySnapshot = copy(
        groups = groups.map { it.copy(tagIds = it.tagIds.toList()) },
        tags = LinkedHashMap(tags.mapValues { (_, tag) -> tag.copy() }),
        ungroupedTagIds = ungroupedTagIds.toList()
    )
}

object DefaultTagLibrary {
    fun snapshot(): TagLibrarySnapshot {
        val tags = linkedMapOf(
            "built-in-goal-understand" to QuickTag("built-in-goal-understand", "理解概念", TagOrigin.BuiltIn),
            "built-in-goal-explain" to QuickTag("built-in-goal-explain", "讲清推导", TagOrigin.BuiltIn),
            "built-in-goal-transfer" to QuickTag("built-in-goal-transfer", "迁移练习", TagOrigin.BuiltIn),
            "built-in-ability-example" to QuickTag("built-in-ability-example", "举例", TagOrigin.BuiltIn),
            "built-in-ability-reflect" to QuickTag("built-in-ability-reflect", "复盘", TagOrigin.BuiltIn)
        )
        return TagLibrarySnapshot(
            groups = listOf(
                TagGroup(
                    id = "built-in-goal",
                    name = "学习目标",
                    colorIndex = 6,
                    origin = TagOrigin.BuiltIn,
                    tagIds = tags.keys.take(3)
                ),
                TagGroup(
                    id = "built-in-ability",
                    name = "能力",
                    colorIndex = 7,
                    origin = TagOrigin.BuiltIn,
                    tagIds = tags.keys.drop(3)
                )
            ),
            tags = tags
        )
    }
}

data class TagSelectionValue(
    val tagId: String?,
    val text: String
)

data class TagFieldSelection(
    val values: List<TagSelectionValue> = emptyList()
) {
    fun toggle(tag: QuickTag, library: TagLibrarySnapshot): TagFieldSelection {
        val without = values.filterNot { it.tagId == tag.id }
        return if (without.size != values.size) copy(values = without)
        else copy(values = (values + TagSelectionValue(tag.id, tag.name)).ordered(library))
    }

    fun orderedValues(library: TagLibrarySnapshot): List<String> =
        values.ordered(library).map(TagSelectionValue::text)

    fun deepCopy(): TagFieldSelection = copy(values = values.map { it.copy() })

    private fun List<TagSelectionValue>.ordered(library: TagLibrarySnapshot): List<TagSelectionValue> {
        val order = library.orderedTags().mapIndexed { index, tag -> tag.id to index }.toMap()
        return withIndex().sortedWith(
            compareBy<IndexedValue<TagSelectionValue>> { order[it.value.tagId] ?: Int.MAX_VALUE }
                .thenBy(IndexedValue<TagSelectionValue>::index)
        ).map(IndexedValue<TagSelectionValue>::value)
    }
}

data class CreateTagGroupRequest(val initialTagIds: List<String>)

enum class TagLibraryPersistenceRetry { Load, Save }

data class TagLibraryEditorState(
    val library: TagLibrarySnapshot = DefaultTagLibrary.snapshot(),
    val expandedGroupIds: Set<String> = emptySet(),
    val pendingGroupCreation: CreateTagGroupRequest? = null,
    val pendingPersistence: TagLibrarySnapshot? = null,
    val persistenceRetry: TagLibraryPersistenceRetry? = null,
    val error: String? = null,
    val notice: String? = null
)

interface TagLibraryPersistence {
    fun loadTagLibrary(): TagLibrarySnapshot?
    fun saveTagLibrary(snapshot: TagLibrarySnapshot)
}

class TagLibraryEditor(
    private val persistence: TagLibraryPersistence,
    private val idFactory: () -> String = { System.currentTimeMillis().toString() }
) {
    var state: TagLibraryEditorState = TagLibraryEditorState()
        private set

    fun load() {
        runCatching { persistence.loadTagLibrary()?.deepCopy() }
            .onSuccess { loaded ->
                state = state.copy(
                    library = loaded ?: DefaultTagLibrary.snapshot(),
                    pendingPersistence = null,
                    persistenceRetry = null,
                    error = null
                )
            }
            .onFailure {
                state = state.copy(
                    persistenceRetry = TagLibraryPersistenceRetry.Load,
                    error = "快捷标签读取失败，当前使用内存状态。请重试读取。"
                )
            }
    }

    fun preferredUnusedColorIndex(): Int {
        val used = state.library.groups.map(TagGroup::colorIndex).toSet()
        return TagColorPalette.swatches.indices.firstOrNull { it !in used } ?: 0
    }

    fun createGroup(name: String, colorIndex: Int = preferredUnusedColorIndex()): String {
        val normalized = name.trim()
        require(normalized.isNotEmpty())
        require(colorIndex in TagColorPalette.swatches.indices)
        val id = idFactory()
        updateLibrary {
            copy(groups = groups + TagGroup(id, normalized, colorIndex, TagOrigin.Custom))
        }
        return id
    }

    fun renameGroup(groupId: String, name: String): Boolean {
        val normalized = name.trim()
        val group = state.library.group(groupId) ?: return false
        if (group.origin == TagOrigin.BuiltIn || normalized.isEmpty()) return false
        return updateLibrary { copy(groups = groups.map { if (it.id == groupId) it.copy(name = normalized) else it }) }
    }

    fun changeGroupColor(groupId: String, colorIndex: Int): Boolean {
        val group = state.library.group(groupId) ?: return false
        if (group.origin == TagOrigin.BuiltIn || colorIndex !in TagColorPalette.swatches.indices) return false
        return updateLibrary { copy(groups = groups.map { if (it.id == groupId) it.copy(colorIndex = colorIndex) else it }) }
    }

    fun reorderGroup(groupId: String, targetIndex: Int): Boolean {
        val group = state.library.group(groupId) ?: return false
        if (group.origin == TagOrigin.BuiltIn) return false
        return updateLibrary {
            val remaining = groups.filterNot { it.id == groupId }.toMutableList()
            remaining.add(targetIndex.coerceIn(0, remaining.size), group)
            copy(groups = remaining)
        }
    }

    fun deleteGroup(groupId: String): Boolean {
        val group = state.library.group(groupId) ?: return false
        if (group.origin == TagOrigin.BuiltIn) return false
        val occupiedNames = state.library.ungroupedTagIds
            .mapNotNull(state.library.tags::get)
            .mapTo(linkedSetOf(), QuickTag::name)
        val renamed = linkedMapOf<String, String>()
        group.tagIds.forEach { tagId ->
            val tag = state.library.tags[tagId] ?: return@forEach
            var resolved = tag.name
            var suffix = 2
            while (resolved in occupiedNames) resolved = "${tag.name} · ${suffix++}"
            occupiedNames += resolved
            if (resolved != tag.name) renamed[tagId] = resolved
        }
        val changed = updateLibrary {
            copy(
                groups = groups.filterNot { it.id == groupId },
                tags = LinkedHashMap(tags).apply {
                    renamed.forEach { (tagId, name) ->
                        get(tagId)?.let { put(tagId, it.copy(name = name)) }
                    }
                },
                ungroupedTagIds = (ungroupedTagIds + group.tagIds).distinct()
            )
        }
        if (changed) {
            state = state.copy(
                expandedGroupIds = state.expandedGroupIds - groupId,
                notice = if (renamed.isEmpty()) "分组已删除，标签已移回未分组。"
                else "分组已删除；${renamed.size} 个同名标签已确定性重命名并移回未分组。"
            )
        }
        return changed
    }

    fun tryAddTag(name: String, groupId: String?): String? {
        val normalized = name.trim()
        if (normalized.isEmpty() || !validCustomDestination(groupId) || hasDuplicate(normalized, groupId)) return null
        val id = idFactory()
        updateLibrary {
            val addedTags = LinkedHashMap(tags).apply { put(id, QuickTag(id, normalized, TagOrigin.Custom)) }
            if (groupId == null) copy(tags = addedTags, ungroupedTagIds = ungroupedTagIds + id)
            else copy(
                tags = addedTags,
                groups = groups.map { if (it.id == groupId) it.copy(tagIds = it.tagIds + id) else it }
            )
        }
        return id
    }

    fun addTag(name: String, groupId: String?): String = requireNotNull(tryAddTag(name, groupId))

    fun renameTag(tagId: String, name: String): Boolean {
        val tag = state.library.tags[tagId] ?: return false
        val normalized = name.trim()
        val groupId = state.library.groupIdFor(tagId)
        if (tag.origin == TagOrigin.BuiltIn || normalized.isEmpty() || hasDuplicate(normalized, groupId, tagId)) return false
        return updateLibrary {
            copy(tags = LinkedHashMap(tags).apply { put(tagId, tag.copy(name = normalized)) })
        }
    }

    fun deleteTag(tagId: String): Boolean {
        val tag = state.library.tags[tagId] ?: return false
        if (tag.origin == TagOrigin.BuiltIn) return false
        return updateLibrary {
            copy(
                groups = groups.map { it.copy(tagIds = it.tagIds - tagId) },
                tags = LinkedHashMap(tags).apply { remove(tagId) },
                ungroupedTagIds = ungroupedTagIds - tagId
            )
        }
    }

    fun reorderTag(tagId: String, targetIndex: Int): Boolean {
        val tag = state.library.tags[tagId] ?: return false
        if (tag.origin == TagOrigin.BuiltIn) return false
        val groupId = state.library.groupIdFor(tagId)
        return updateLibrary {
            if (groupId == null) {
                val remaining = ungroupedTagIds.filterNot { it == tagId }.toMutableList()
                remaining.add(targetIndex.coerceIn(0, remaining.size), tagId)
                copy(ungroupedTagIds = remaining)
            } else {
                copy(groups = groups.map { group ->
                    if (group.id != groupId) group else {
                        val remaining = group.tagIds.filterNot { it == tagId }.toMutableList()
                        remaining.add(targetIndex.coerceIn(0, remaining.size), tagId)
                        group.copy(tagIds = remaining)
                    }
                })
            }
        }
    }

    fun moveTag(tagId: String, targetGroupId: String?, targetIndex: Int): Boolean {
        val tag = state.library.tags[tagId] ?: return false
        if (tag.origin == TagOrigin.BuiltIn || !validCustomDestination(targetGroupId)) return false
        if (hasDuplicate(tag.name, targetGroupId, tagId)) return false
        return updateLibrary {
            val clearedGroups = groups.map { it.copy(tagIds = it.tagIds - tagId) }
            val clearedUngrouped = ungroupedTagIds - tagId
            if (targetGroupId == null) {
                val destination = clearedUngrouped.toMutableList()
                destination.add(targetIndex.coerceIn(0, destination.size), tagId)
                copy(groups = clearedGroups, ungroupedTagIds = destination)
            } else {
                copy(
                    groups = clearedGroups.map { group ->
                        if (group.id != targetGroupId) group else {
                            val destination = group.tagIds.toMutableList()
                            destination.add(targetIndex.coerceIn(0, destination.size), tagId)
                            group.copy(tagIds = destination)
                        }
                    },
                    ungroupedTagIds = clearedUngrouped
                )
            }
        }
    }

    fun dropUngroupedTagOnto(draggedTagId: String, targetTagId: String): CreateTagGroupRequest? {
        if (draggedTagId == targetTagId) return null
        val ungrouped = state.library.ungroupedTagIds
        if (draggedTagId !in ungrouped || targetTagId !in ungrouped) return null
        return CreateTagGroupRequest(listOf(draggedTagId, targetTagId)).also {
            state = state.copy(pendingGroupCreation = it)
        }
    }

    fun dropTagOnto(draggedTagId: String, targetTagId: String): Boolean {
        if (draggedTagId == targetTagId) return false
        val dragged = state.library.tags[draggedTagId] ?: return false
        val target = state.library.tags[targetTagId] ?: return false
        if (dragged.origin == TagOrigin.BuiltIn || target.origin == TagOrigin.BuiltIn) return false
        val sourceGroupId = state.library.groupIdFor(draggedTagId)
        val targetGroupId = state.library.groupIdFor(targetTagId)
        if (sourceGroupId == null && targetGroupId == null) {
            return dropUngroupedTagOnto(draggedTagId, targetTagId) != null
        }
        val destinationIds = if (targetGroupId == null) state.library.ungroupedTagIds
        else state.library.group(targetGroupId)?.tagIds ?: return false
        val targetIndex = destinationIds.filterNot { it == draggedTagId }.indexOf(targetTagId)
        if (targetIndex < 0) return false
        return moveTag(draggedTagId, targetGroupId, targetIndex)
    }

    fun dropTagIntoRange(draggedTagId: String, targetGroupId: String?): Boolean =
        moveTag(draggedTagId, targetGroupId, Int.MAX_VALUE)

    fun confirmPendingGroup(name: String): String? {
        val request = state.pendingGroupCreation ?: return null
        val groupId = createGroup(name)
        request.initialTagIds.forEachIndexed { index, tagId -> moveTag(tagId, groupId, index) }
        state = state.copy(pendingGroupCreation = null)
        return groupId
    }

    fun dismissPendingGroup() {
        state = state.copy(pendingGroupCreation = null)
    }

    fun setGroupExpanded(groupId: String, expanded: Boolean) {
        if (groupId != UngroupedTagRangeId && state.library.group(groupId) == null) return
        state = state.copy(
            expandedGroupIds = if (expanded) state.expandedGroupIds + groupId else state.expandedGroupIds - groupId
        )
    }

    fun visibleTags(groupId: String): List<QuickTag> {
        val ids = if (groupId == UngroupedTagRangeId) state.library.ungroupedTagIds
        else state.library.group(groupId)?.tagIds ?: return emptyList()
        val tags = ids.mapNotNull(state.library.tags::get)
        return tags
    }

    fun handleBack(): Boolean {
        if (state.expandedGroupIds.isEmpty()) return false
        state = state.copy(expandedGroupIds = emptySet())
        return true
    }

    fun retryPersistence(): Boolean = when (state.persistenceRetry) {
        TagLibraryPersistenceRetry.Load -> retryLoad()
        TagLibraryPersistenceRetry.Save -> retrySave()
        null -> false
    }

    fun retryLoad(): Boolean {
        if (state.persistenceRetry != TagLibraryPersistenceRetry.Load) return false
        load()
        return state.persistenceRetry == null
    }

    fun retrySave(): Boolean {
        if (state.persistenceRetry != TagLibraryPersistenceRetry.Save) return false
        return persistPending()
    }

    private fun validCustomDestination(groupId: String?): Boolean =
        groupId == null || state.library.group(groupId)?.origin == TagOrigin.Custom

    private fun hasDuplicate(name: String, groupId: String?, excludingTagId: String? = null): Boolean {
        val ids = groupId?.let { state.library.group(it)?.tagIds }.orEmpty().ifEmpty {
            if (groupId == null) state.library.ungroupedTagIds else emptyList()
        }
        return ids.any { id -> id != excludingTagId && state.library.tags[id]?.name == name }
    }

    private fun updateLibrary(transform: TagLibrarySnapshot.() -> TagLibrarySnapshot): Boolean {
        val before = state.library
        val after = before.transform()
        if (after == before) return false
        state = state.copy(
            library = after,
            pendingPersistence = after.deepCopy(),
            persistenceRetry = null,
            error = null,
            notice = null
        )
        persistPending()
        return true
    }

    private fun persistPending(): Boolean {
        val pending = state.pendingPersistence ?: return false
        return runCatching { persistence.saveTagLibrary(pending.deepCopy()) }
            .fold(
                onSuccess = {
                    state = state.copy(
                        pendingPersistence = null,
                        persistenceRetry = null,
                        error = null
                    )
                    true
                },
                onFailure = {
                    state = state.copy(
                        error = "快捷标签尚未保存。内存编辑已保留，请重试。",
                        pendingPersistence = pending.deepCopy(),
                        persistenceRetry = TagLibraryPersistenceRetry.Save
                    )
                    false
                }
            )
    }
}
