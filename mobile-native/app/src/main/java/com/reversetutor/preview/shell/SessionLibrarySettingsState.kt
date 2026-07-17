package com.reversetutor.preview.shell

data class SessionLibrarySettingsState(
    val sources: List<SessionLibrarySource>
) {
    val enabledCount: Int
        get() = sources.count { it.enabled }

    val enabledSummary: String
        get() = "本会话已启用 $enabledCount 份资料"

    fun toggle(sourceId: String, enabled: Boolean): SessionLibrarySettingsState = copy(
        sources = sources.map { source ->
            if (source.id == sourceId) source.copy(enabled = enabled) else source
        }
    )

    fun retry(sourceId: String): SessionLibrarySettingsState = copy(
        sources = sources.map { source ->
            if (source.id == sourceId) {
                source.copy(status = SessionSourceStatus.Importing(0))
            } else {
                source
            }
        }
    )

    companion object {
        fun reference(): SessionLibrarySettingsState = SessionLibrarySettingsState(
            sources = listOf(
                SessionLibrarySource(
                    id = "python-basics",
                    title = "Python 基础概念.pdf",
                    detail = "24 页",
                    type = SessionLibrarySourceType.Pdf,
                    enabled = true,
                    status = SessionSourceStatus.Active
                ),
                SessionLibrarySource(
                    id = "class-notes",
                    title = "课堂笔记：变量与类型",
                    detail = "8 页",
                    type = SessionLibrarySourceType.Note,
                    enabled = true,
                    status = SessionSourceStatus.Active
                ),
                SessionLibrarySource(
                    id = "mistake-image",
                    title = "错题截图 07-12.jpg",
                    detail = "2.4 MB",
                    type = SessionLibrarySourceType.Image,
                    enabled = true,
                    status = SessionSourceStatus.Importing(60)
                ),
                SessionLibrarySource(
                    id = "practice-code",
                    title = "list_practice.py",
                    detail = "3.1 KB",
                    type = SessionLibrarySourceType.Python,
                    enabled = true,
                    status = SessionSourceStatus.Active
                ),
                SessionLibrarySource(
                    id = "function-scope",
                    title = "函数与作用域.pdf",
                    detail = "16 页",
                    type = SessionLibrarySourceType.Pdf,
                    enabled = false,
                    status = SessionSourceStatus.ReadFailed
                ),
                SessionLibrarySource(
                    id = "review-outline",
                    title = "复习提纲.md",
                    detail = "5.8 KB",
                    type = SessionLibrarySourceType.Markdown,
                    enabled = true,
                    status = SessionSourceStatus.Active
                )
            )
        )
    }
}

data class SessionLibrarySource(
    val id: String,
    val title: String,
    val detail: String,
    val type: SessionLibrarySourceType,
    val enabled: Boolean,
    val status: SessionSourceStatus
)

enum class SessionLibrarySourceType {
    Pdf,
    Note,
    Image,
    Python,
    Markdown
}

sealed interface SessionSourceStatus {
    data object Active : SessionSourceStatus
    data class Importing(val progress: Int) : SessionSourceStatus
    data object ReadFailed : SessionSourceStatus
}
