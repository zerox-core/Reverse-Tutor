package com.reversetutor.preview.shell

enum class SessionSettingsTab {
    Library,
    Graph,
    Persona,
    Personalization
}

data class FigmaAppUiState(
    val challengeJoined: Boolean = false,
    val challengeProgress: Int = 12,
    val challengeTotal: Int = 21,
    val activityAnnouncementDismissed: Boolean = false,
    val sessionSettingsTab: SessionSettingsTab = SessionSettingsTab.Library
) {
    val challengeProgressLabel: String
        get() = "$challengeProgress/$challengeTotal"

    fun joinChallenge(): FigmaAppUiState = copy(challengeJoined = true)

    fun dismissActivityAnnouncement(): FigmaAppUiState =
        copy(activityAnnouncementDismissed = true)

    fun selectSessionSettings(tab: SessionSettingsTab): FigmaAppUiState =
        copy(sessionSettingsTab = tab)
}
