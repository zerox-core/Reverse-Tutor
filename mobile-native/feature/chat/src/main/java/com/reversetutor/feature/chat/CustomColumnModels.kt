package com.reversetutor.feature.chat

data class CustomColumn(
    val id: String,
    val name: String,
    val content: String,
    val tags: TagFieldSelection = TagFieldSelection()
) {
    fun deepCopy(): CustomColumn = copy(tags = tags.deepCopy())
}

data class CustomColumnEditorState(
    val columns: List<CustomColumn> = emptyList(),
    val pendingDeleteColumnId: String? = null,
    val error: String? = null
)

class CustomColumnEditor(
    initialColumns: List<CustomColumn> = emptyList(),
    private val idFactory: () -> String = { System.currentTimeMillis().toString() }
) {
    var state: CustomColumnEditorState = CustomColumnEditorState(
        columns = initialColumns.map(CustomColumn::deepCopy)
    )
        private set

    fun replaceColumns(columns: List<CustomColumn>) {
        state = state.copy(columns = columns.map(CustomColumn::deepCopy), error = null)
    }

    fun addColumn(name: String, content: String, tags: TagFieldSelection = TagFieldSelection()): String? {
        val normalized = name.trim()
        if (normalized.isEmpty() || hasDuplicate(normalized)) {
            state = state.copy(error = "栏目名称不能为空或重复。")
            return null
        }
        val id = idFactory()
        state = state.copy(
            columns = state.columns + CustomColumn(id, normalized, content, tags.deepCopy()),
            error = null
        )
        return id
    }

    fun editColumn(id: String, name: String, content: String, tags: TagFieldSelection): Boolean {
        val normalized = name.trim()
        if (normalized.isEmpty() || hasDuplicate(normalized, id)) {
            state = state.copy(error = "栏目名称不能为空或重复。")
            return false
        }
        if (state.columns.none { it.id == id }) return false
        state = state.copy(
            columns = state.columns.map {
                if (it.id == id) it.copy(name = normalized, content = content, tags = tags.deepCopy()) else it
            },
            error = null
        )
        return true
    }

    fun reorderColumn(id: String, targetIndex: Int): Boolean {
        val column = state.columns.firstOrNull { it.id == id } ?: return false
        val remaining = state.columns.filterNot { it.id == id }.toMutableList()
        remaining.add(targetIndex.coerceIn(0, remaining.size), column)
        state = state.copy(columns = remaining, error = null)
        return true
    }

    fun dropColumnOnto(draggedId: String, targetId: String): Boolean {
        if (draggedId == targetId) return false
        val dragged = state.columns.firstOrNull { it.id == draggedId } ?: return false
        val remaining = state.columns.filterNot { it.id == draggedId }.toMutableList()
        val targetIndex = remaining.indexOfFirst { it.id == targetId }
        if (targetIndex < 0) return false
        remaining.add(targetIndex, dragged)
        state = state.copy(columns = remaining, error = null)
        return true
    }

    fun copyColumn(id: String): String? {
        val source = state.columns.firstOrNull { it.id == id } ?: return null
        val base = "${source.name} 副本"
        var name = base
        var suffix = 2
        while (hasDuplicate(name)) name = "$base ${suffix++}"
        val copy = source.deepCopy().copy(id = idFactory(), name = name)
        val index = state.columns.indexOfFirst { it.id == id } + 1
        val updated = state.columns.toMutableList().apply { add(index, copy) }
        state = state.copy(columns = updated, error = null)
        return copy.id
    }

    fun requestDelete(id: String): Boolean {
        if (state.columns.none { it.id == id }) return false
        state = state.copy(pendingDeleteColumnId = id)
        return true
    }

    fun cancelDelete() {
        state = state.copy(pendingDeleteColumnId = null)
    }

    fun confirmDelete(): Boolean {
        val id = state.pendingDeleteColumnId ?: return false
        state = state.copy(
            columns = state.columns.filterNot { it.id == id },
            pendingDeleteColumnId = null,
            error = null
        )
        return true
    }

    private fun hasDuplicate(name: String, excludingId: String? = null): Boolean =
        state.columns.any { it.id != excludingId && it.name == name }
}

fun NewSessionConfiguration.deepCopy(): NewSessionConfiguration = copy(
    sourceSelections = sourceSelections.toList(),
    customFields = LinkedHashMap(customFields),
    customColumns = customColumns.map(CustomColumn::deepCopy),
    quickTags = quickTags.mapValues { it.value.deepCopy() }
)

fun NewSessionConfiguration.withCustomColumns(columns: List<CustomColumn>): NewSessionConfiguration = copy(
    customColumns = columns.map(CustomColumn::deepCopy),
    customFields = linkedMapOf<String, String>().apply {
        columns.forEach { put(it.name, it.content) }
    }
)
