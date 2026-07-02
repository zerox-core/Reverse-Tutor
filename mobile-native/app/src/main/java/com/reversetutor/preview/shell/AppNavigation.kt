package com.reversetutor.preview.shell

enum class AppDestination(
    val route: String,
    val title: String,
    val status: String
) {
    Sessions("sessions", "Sessions", "Native preview home"),
    Chat("chat", "Chat", "Core loop preview"),
    ContextHub("context", "Context hub", "Session context preview"),
    GlobalGraph("global-graph", "Global graph", "Cross-session preview"),
    Sources("sources", "Sources", "Source library preview"),
    Settings("settings", "Settings", "Settings preview"),
    ImportExport("import-export", "Import/export", "Migration preview"),
    About("about", "About", "Diagnostics preview")
}

enum class AppModal {
    Status
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
    val modal: AppModal? = null
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
            else -> listOf(AppDestination.Sessions, destination)
        }
        return copy(backStack = nextStack, modal = null)
    }

    fun openModal(modal: AppModal): AppNavigationState = copy(modal = modal)

    fun closeModal(): AppNavigationState = copy(modal = null)

    fun handleSystemBack(): BackTransition {
        if (modal != null) {
            return BackTransition(closeModal(), BackResult.Consumed)
        }

        if (current == AppDestination.ContextHub) {
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

        if (backStack.size > 1) {
            return BackTransition(copy(backStack = backStack.dropLast(1)), BackResult.Consumed)
        }

        return BackTransition(this, BackResult.AllowSystemExit)
    }
}
