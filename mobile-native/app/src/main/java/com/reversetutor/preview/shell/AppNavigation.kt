package com.reversetutor.preview.shell

enum class AppDestination(
    val route: String,
    val title: String,
    val status: String
) {
    Sessions("sessions", "会话", "会话列表 · 本地优先"),
    Chat("chat", "聊天", "当前学习会话"),
    ChatReferences("chat-references", "资料与引用", "当前会话查询"),
    ContextHub("brain", "学习大脑", "全局图谱和薄弱节点"),
    WeeklyDashboard("weekly-dashboard", "本周", "跨会话学习概览"),
    GlobalGraph("global-graph", "全局图谱", "跨会话只读图谱"),
    Sources("sources", "资料", "本地资料库"),
    Community("community", "社区", "挑战、模板和公开学习树"),
    SessionSettings("session-settings", "会话设置", "当前会话设置"),
    SessionSettingsLibrary("session-settings-library", "会话设置", "资料库"),
    SessionSettingsSources("session-settings-sources", "会话设置", "资料管理"),
    SessionSettingsGraph("session-settings-graph", "会话设置", "图谱"),
    SessionSettingsPersona("session-settings-persona", "会话设置", "人格目标"),
    SessionSettingsPersonalization("session-settings-personalization", "会话设置", "个性化"),
    Challenge("challenge", "挑战活动", "线上活动 · 学习任务"),
    NewSession("new-session", "新建会话", "模板、导入和自定义"),
    GlobalSearch("global-search", "全局搜索", "本地索引"),
    PublicArticle("public-article", "公益读本", "在线公益内容"),
    Settings("settings", "设置", "模型、迁移、外观和诊断"),
    LlmConfiguration("llm-configuration", "LLM API", "模型服务与账户"),
    ImportExport("import-export", "导入与导出", "迁移和备份"),
    About("about", "关于诊断", "本机诊断信息"),
    TokenUsage("token-usage", "Token 统计", "本地模型用量"),
    Update("update", "应用更新", "在线更新检查");

    companion object {
        val primaryDestinations: List<AppDestination> = listOf(
            Sessions,
            Chat,
            Challenge,
            Settings
        )

        val drawerItems: List<AppDrawerItem> = listOf(
            AppDrawerItem(route = "sessions", label = "会话", destination = Sessions),
            AppDrawerItem(route = "community", label = "社区", destination = Community),
            AppDrawerItem(route = "settings", label = "设置", destination = Settings)
        )
    }
}

data class AppDrawerItem(
    val route: String,
    val label: String,
    val destination: AppDestination,
    val enabled: Boolean = true
)

enum class AppModal {
    Status,
    ChallengeDetail,
    ActivityAnnouncement
}

enum class BackResult {
    Consumed,
    AllowSystemExit
}

data class BackTransition(
    val state: AppNavigationState,
    val result: BackResult
)

data class AppNavigationState(
    val backStack: List<AppDestination> = listOf(AppDestination.Sessions),
    val modal: AppModal? = null,
    val drawerOpen: Boolean = false
) {
    val current: AppDestination
        get() = backStack.lastOrNull() ?: AppDestination.Sessions

    fun navigate(destination: AppDestination): AppNavigationState {
        val nextStack = when (destination) {
            AppDestination.Sessions -> listOf(AppDestination.Sessions)
            AppDestination.Chat -> listOf(AppDestination.Sessions, AppDestination.Chat)
            AppDestination.ContextHub -> listOf(
                AppDestination.Sessions,
                AppDestination.Chat,
                AppDestination.ContextHub
            )
            AppDestination.WeeklyDashboard,
            AppDestination.GlobalGraph,
            AppDestination.Community -> listOf(
                AppDestination.Sessions,
                destination
            )
            AppDestination.Challenge,
            AppDestination.NewSession,
            AppDestination.GlobalSearch,
            AppDestination.PublicArticle -> listOf(AppDestination.Sessions, destination)
            AppDestination.ChatReferences,
            AppDestination.SessionSettings,
            AppDestination.SessionSettingsLibrary,
            AppDestination.SessionSettingsSources,
            AppDestination.SessionSettingsGraph,
            AppDestination.SessionSettingsPersona,
            AppDestination.SessionSettingsPersonalization -> listOf(
                AppDestination.Sessions,
                AppDestination.Chat,
                destination
            )
            AppDestination.LlmConfiguration,
            AppDestination.ImportExport,
            AppDestination.About,
            AppDestination.TokenUsage,
            AppDestination.Update -> listOf(
                AppDestination.Sessions,
                AppDestination.Settings,
                destination
            )
            else -> listOf(AppDestination.Sessions, destination)
        }
        return copy(backStack = nextStack, modal = null, drawerOpen = false)
    }

    fun openModal(modal: AppModal): AppNavigationState = copy(modal = modal, drawerOpen = false)

    fun closeModal(): AppNavigationState = copy(modal = null)

    fun openDrawer(): AppNavigationState = copy(drawerOpen = true, modal = null)

    fun closeDrawer(): AppNavigationState = copy(drawerOpen = false)

    fun handleSystemBack(): BackTransition {
        if (drawerOpen) {
            return BackTransition(closeDrawer(), BackResult.Consumed)
        }

        if (modal != null) {
            return BackTransition(closeModal(), BackResult.Consumed)
        }

        if (current == AppDestination.ChatReferences ||
            current == AppDestination.SessionSettings ||
            current == AppDestination.SessionSettingsLibrary ||
            current == AppDestination.SessionSettingsSources ||
            current == AppDestination.SessionSettingsGraph ||
            current == AppDestination.SessionSettingsPersona ||
            current == AppDestination.SessionSettingsPersonalization
        ) {
            return BackTransition(
                copy(backStack = listOf(AppDestination.Sessions, AppDestination.Chat)),
                BackResult.Consumed
            )
        }

        if (current == AppDestination.Chat) {
            return BackTransition(
                copy(backStack = listOf(AppDestination.Sessions)),
                BackResult.Consumed
            )
        }


        if (current == AppDestination.ContextHub) {
            return BackTransition(
                copy(backStack = listOf(AppDestination.Sessions, AppDestination.Chat)),
                BackResult.Consumed
            )
        }

        if (current == AppDestination.WeeklyDashboard ||
            current == AppDestination.GlobalGraph ||
            current == AppDestination.Community ||
            current == AppDestination.Challenge
        ) {
            return BackTransition(
                copy(backStack = listOf(AppDestination.Sessions)),
                BackResult.Consumed
            )
        }

        if (current == AppDestination.LlmConfiguration) {
            return BackTransition(
                copy(backStack = listOf(AppDestination.Sessions, AppDestination.Settings)),
                BackResult.Consumed
            )
        }

        if (backStack.size > 1) {
            return BackTransition(copy(backStack = backStack.dropLast(1)), BackResult.Consumed)
        }

        return BackTransition(this, BackResult.AllowSystemExit)
    }
}
