package com.reversetutor.preview.shell

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.reversetutor.core.data.background.BackgroundGenerationRepository
import com.reversetutor.core.data.graph.GraphRepository
import com.reversetutor.core.data.llm.ChatGenerationRepository
import com.reversetutor.core.data.llm.LlmProfileRepository
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.data.sources.SourceImportInput
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.data.preferences.AppPreferences
import com.reversetutor.core.llm.LlmProviderPreset
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.SearchTarget
import com.reversetutor.core.model.SearchTargetType
import com.reversetutor.feature.chat.ChatRoute
import com.reversetutor.feature.chat.ChatImageDraft
import com.reversetutor.feature.chat.FigmaNewSessionRoute
import com.reversetutor.feature.chat.SessionsRoute
import com.reversetutor.feature.chat.toSessionListItem
import com.reversetutor.feature.memory.FormalWeeklyDashboardScreen
import com.reversetutor.feature.memory.FormalWeeklySessionOption
import com.reversetutor.feature.memory.GlobalGraphRoute
import com.reversetutor.feature.memory.WeeklyDashboardUiState
import com.reversetutor.feature.sources.SourcesRoute
import com.reversetutor.feature.settings.FirstLaunchImportPromptUiState
import com.reversetutor.feature.settings.FormalLlmConfigurationScreen
import com.reversetutor.feature.settings.LlmProfileSettingsUiState
import com.reversetutor.feature.settings.FormalSettingsScreen
import com.reversetutor.preview.background.BackgroundGenerationWorker
import com.reversetutor.preview.theme.ReverseTutorDesign
import com.reversetutor.preview.theme.ReverseTutorStatusTone
import com.reversetutor.preview.ui.ReverseTutorActionButton
import com.reversetutor.preview.ui.ReverseTutorActionTone
import com.reversetutor.preview.ui.ReverseTutorConfirmationDialog
import com.reversetutor.preview.ui.ReverseTutorScaffold
import com.reversetutor.preview.ui.ReverseTutorScreenSurface
import com.reversetutor.preview.ui.ReverseTutorStatusStrip
import com.reversetutor.preview.ui.ReverseTutorTopAppBar
import com.reversetutor.preview.wiring.HybridAppGraph
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun AppShell(
    hybridAppGraph: HybridAppGraph,
    appPreferences: AppPreferences = AppPreferences.defaults,
    sessionRepository: SessionRepository,
    messageRepository: MessageRepository,
    llmProfileRepository: LlmProfileRepository,
    chatGenerationRepository: ChatGenerationRepository,
    backgroundGenerationRepository: BackgroundGenerationRepository? = null,
    sourceRepository: SourceRepository,
    memoryRepository: MemoryRepository,
    graphRepository: GraphRepository,
    firstLaunchImportPromptState: FirstLaunchImportPromptUiState = FirstLaunchImportPromptUiState.preview(),
    initialImportText: String? = null,
    initialImportFileName: String? = null,
    onExitRequested: () -> Unit
) {
    var navigationState by remember { mutableStateOf(AppNavigationState()) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var activeSessionTitle by remember { mutableStateOf<String?>(null) }
    var activeArticleSlug by remember { mutableStateOf("") }
    var figmaUiState by remember { mutableStateOf(FigmaAppUiState()) }
    var workspaceChromeObscuredPages by remember { mutableStateOf(emptySet<WorkspacePage>()) }
    var showFirstLaunchImportPrompt by remember(firstLaunchImportPromptState) {
        mutableStateOf(firstLaunchImportPromptState.shouldShow)
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val appScope = rememberCoroutineScope()
    val workspaceViewModelFactory = hybridAppGraph.frontend.workspaceViewModelFactory
    val workspaceViewModel = remember(workspaceViewModelFactory) {
        workspaceViewModelFactory.create()
    }
    val workspaceState by workspaceViewModel.uiState.collectAsState()
    val challengeRuntimeCoordinator = hybridAppGraph.challengeRuntimeCoordinator
    val challengeRuntimeState by challengeRuntimeCoordinator.state.collectAsState()
    val weeklyDashboardViewModelFactory = hybridAppGraph.frontend.weeklyDashboardViewModelFactory
    val weeklyDashboardViewModel = remember(weeklyDashboardViewModelFactory) {
        weeklyDashboardViewModelFactory.create(appScope)
    }
    val weeklyDashboardState by weeklyDashboardViewModel.uiState.collectAsState()
    val workspaceInteractions = remember(workspaceViewModel) {
        WorkspaceInteractionBindings(workspaceViewModel::onAction)
    }

    LaunchedEffect(challengeRuntimeCoordinator) {
        challengeRuntimeCoordinator.load()
    }

    BackHandler {
        val transition = navigationState.handleSystemBack()
        if (transition.result == BackResult.AllowSystemExit) {
            onExitRequested()
        } else {
            navigationState = transition.state
        }
    }

    LaunchedEffect(initialImportText, initialImportFileName) {
        if (!initialImportText.isNullOrBlank()) {
            navigationState = navigationState.navigate(AppDestination.ImportExport)
        }
    }

    LaunchedEffect(navigationState.current) {
        workspaceSelectionFor(navigationState.current)?.let { selection ->
            if (selection.horizontal != workspaceState.currentPage) {
                workspaceViewModel.onAction(WorkspaceUiAction.SelectPage(selection.horizontal))
            }
            selection.vertical?.let { verticalPage ->
                if (verticalPage != workspaceState.verticalPage) {
                    workspaceViewModel.onAction(WorkspaceUiAction.SelectVerticalPage(verticalPage))
                }
            }
        }
    }

    LaunchedEffect(navigationState.drawerOpen) {
        if (navigationState.drawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentValue }
            .map { it == DrawerValue.Open }
            .distinctUntilChanged()
            .collect { open ->
                if (!open && navigationState.drawerOpen) {
                    navigationState = navigationState.closeDrawer()
                }
            }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = navigationState.drawerOpen,
        drawerContent = {
            AppDrawer(
                current = navigationState.current,
                onDestinationClick = {
                    navigationState = navigationState.navigate(it)
                },
                onImportExportClick = {
                    navigationState = navigationState.navigate(AppDestination.ImportExport)
                }
            )
        }
    ) {
        ReverseTutorScaffold(
            topBar = {
                if (!navigationState.current.ownsInContentTopBar()) {
                    PreviewTopBar(
                        destination = navigationState.current,
                        activeSessionTitle = activeSessionTitle,
                        onNavigationClick = {
                            if (navigationState.current.usesDrawerNavigation()) {
                                navigationState = navigationState.openDrawer()
                            } else {
                                val transition = navigationState.handleSystemBack()
                                navigationState = transition.state
                            }
                        },
                        onStatusClick = {
                            if (navigationState.current == AppDestination.Chat) {
                                navigationState =
                                    navigationState.navigate(AppDestination.SessionSettingsLibrary)
                            } else {
                                navigationState = navigationState.openModal(AppModal.Status)
                            }
                        },
                        onChallengeClick = {
                            navigationState = navigationState.navigate(AppDestination.Challenge)
                        },
                        onNewSessionClick = {
                            navigationState = navigationState.navigate(AppDestination.NewSession)
                        }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val renderDestination: @Composable (AppDestination) -> Unit = { destination ->
                    DestinationContent(
                        destination = destination,
                        hybridAppGraph = hybridAppGraph,
                        appPreferences = appPreferences,
                        sessionRepository = sessionRepository,
                        messageRepository = messageRepository,
                        llmProfileRepository = llmProfileRepository,
                        chatGenerationRepository = chatGenerationRepository,
                        backgroundGenerationRepository = backgroundGenerationRepository,
                        sourceRepository = sourceRepository,
                        memoryRepository = memoryRepository,
                        graphRepository = graphRepository,
                        initialImportText = initialImportText,
                        initialImportFileName = initialImportFileName,
                        activeSessionId = activeSessionId,
                        activeSessionTitle = activeSessionTitle,
                        activeArticleSlug = activeArticleSlug,
                        challengeRuntimeState = challengeRuntimeState,
                        challengeProgress = challengeRuntimeState.participation?.progress?.toInt()
                            ?: figmaUiState.challengeProgress,
                        challengeTotal = figmaUiState.challengeTotal,
                        weeklyDashboardState = weeklyDashboardState,
                        onComposerFocusChanged = workspaceInteractions::onComposerFocusChanged,
                        onGraphInteractionChanged =
                            workspaceInteractions::onFullscreenGraphInteractionChanged,
                        onChallengeExitBoundaryChanged = { canReturnHome ->
                            workspaceViewModel.onAction(
                                WorkspaceUiAction.SetChallengeExitBoundary(canReturnHome)
                            )
                        },
                        onWorkspaceChromeObscuredChanged = { page, obscured ->
                            workspaceChromeObscuredPages = if (obscured) {
                                workspaceChromeObscuredPages + page
                            } else {
                                workspaceChromeObscuredPages - page
                            }
                        },
                        onOpenChat = {
                            navigationState = navigationState.navigate(AppDestination.Chat)
                        },
                        onOpenSession = { session ->
                            activeSessionId = session.id
                            activeSessionTitle = session.title
                            navigationState = navigationState.navigate(AppDestination.Chat)
                        },
                        onOpenContextHub = {
                            navigationState =
                                navigationState.navigate(AppDestination.SessionSettingsGraph)
                        },
                        onOpenGlobalGraph = {
                            navigationState = navigationState.navigate(AppDestination.GlobalGraph)
                        },
                        onOpenSources = {
                            navigationState = navigationState.navigate(AppDestination.Sources)
                        },
                        onOpenSessions = {
                            navigationState = navigationState.navigate(AppDestination.Sessions)
                        },
                        onOpenChallenge = {
                            navigationState = navigationState.navigate(AppDestination.Challenge)
                        },
                        onChallengeJoined = {
                            appScope.launch {
                                challengeRuntimeCoordinator.join()
                                if (challengeRuntimeCoordinator.state.value.joined) {
                                    workspaceViewModel.onAction(
                                        WorkspaceUiAction.SelectVerticalPage(
                                            WorkspaceVerticalPage.SessionHome
                                        )
                                    )
                                    navigationState =
                                        navigationState.navigate(AppDestination.Sessions)
                                }
                            }
                        },
                        onChallengeRetry = {
                            appScope.launch { challengeRuntimeCoordinator.retry() }
                        },
                        onOpenNewSession = {
                            navigationState = navigationState.navigate(AppDestination.NewSession)
                        },
                        onOpenPublicArticle = { slug ->
                            activeArticleSlug = slug
                            navigationState = navigationState.navigate(AppDestination.PublicArticle)
                        },
                        onOpenSearchTarget = { target ->
                            appScope.launch {
                                when (target.type) {
                                    SearchTargetType.Session,
                                    SearchTargetType.Message -> {
                                        val sessionId = target.sessionId ?: target.entityId
                                        sessionRepository.getSession(sessionId)?.let { session ->
                                            activeSessionId = session.id
                                            activeSessionTitle = session.title
                                            navigationState = navigationState.navigate(AppDestination.Chat)
                                        }
                                    }
                                    SearchTargetType.Source -> {
                                        navigationState = navigationState.navigate(AppDestination.Sources)
                                    }
                                    SearchTargetType.Memory,
                                    SearchTargetType.GraphNode -> {
                                        navigationState = navigationState.navigate(AppDestination.GlobalGraph)
                                    }
                                    SearchTargetType.StudyPlan -> {
                                        navigationState = navigationState.navigate(AppDestination.WeeklyDashboard)
                                    }
                                }
                            }
                        },
                        onSessionCreated = { session ->
                            activeSessionId = session.id
                            activeSessionTitle = session.title
                            navigationState = navigationState.navigate(AppDestination.Chat)
                        },
                        onOpenSettings = {
                            navigationState = navigationState.navigate(AppDestination.Settings)
                        },
                        onOpenImportExport = {
                            navigationState = navigationState.navigate(AppDestination.ImportExport)
                        },
                        onOpenAbout = {
                            navigationState = navigationState.navigate(AppDestination.About)
                        },
                        onNavigateDestination = { destination ->
                            navigationState = navigationState.navigate(destination)
                        }
                    )
                }
                if (navigationState.current.workspacePage != null) {
                    WorkspacePagerHost(
                        state = workspaceState.selectedForDestination(navigationState.current),
                        interactions = workspaceInteractions,
                        onPageSelected = { page ->
                            workspaceViewModel.onAction(WorkspaceUiAction.SelectPage(page))
                            if (navigationState.current != page.destination) {
                                navigationState = navigationState.navigate(page.destination)
                            }
                        },
                        onVerticalPageSelected = { page ->
                            workspaceViewModel.onAction(WorkspaceUiAction.SelectVerticalPage(page))
                            val destination = when (page) {
                                WorkspaceVerticalPage.Challenge -> AppDestination.Challenge
                                WorkspaceVerticalPage.SessionHome -> AppDestination.Sessions
                            }
                            if (navigationState.current != destination) {
                                navigationState = navigationState.navigate(destination)
                            }
                        },
                        showIndicator = workspaceChromeObscuredPages.isEmpty(),
                        challengeContent = {
                            renderDestination(AppDestination.Challenge)
                        },
                        pageContent = { page -> renderDestination(page.destination) }
                    )
                } else {
                    renderDestination(navigationState.current)
                }
            }
        }
    }

    if (navigationState.modal == AppModal.Status) {
        StatusDialog(
            destination = navigationState.current,
            onDismiss = {
                navigationState = navigationState.closeModal()
            }
        )
    }

    if (showFirstLaunchImportPrompt) {
        FirstLaunchImportPromptDialog(
            state = firstLaunchImportPromptState,
            onConfirm = {
                showFirstLaunchImportPrompt = false
                navigationState = navigationState.navigate(AppDestination.ImportExport)
            },
            onDismiss = {
                showFirstLaunchImportPrompt = false
            }
        )
    }

    if (navigationState.current == AppDestination.NewSession &&
        !figmaUiState.activityAnnouncementDismissed &&
        !showFirstLaunchImportPrompt
    ) {
        ActivityAnnouncementDialog(
            onDismiss = {
                figmaUiState = figmaUiState.dismissActivityAnnouncement()
            },
            onViewChallenge = {
                figmaUiState = figmaUiState.dismissActivityAnnouncement()
                navigationState = navigationState.navigate(AppDestination.Challenge)
            }
        )
    }
}

@Composable
private fun PreviewTopBar(
    destination: AppDestination,
    activeSessionTitle: String?,
    onNavigationClick: () -> Unit,
    onStatusClick: () -> Unit,
    onChallengeClick: () -> Unit,
    onNewSessionClick: () -> Unit
) {
    val title = if (destination == AppDestination.Chat && !activeSessionTitle.isNullOrBlank()) {
        activeSessionTitle
    } else {
        destination.title
    }

    ReverseTutorTopAppBar(
        title = title,
        subtitle = destination.status,
        navigationLabel = if (destination.usesDrawerNavigation()) "菜单" else "返回",
        onNavigationClick = onNavigationClick,
        actionLabel = when (destination) {
            AppDestination.Chat -> "⚙"
            AppDestination.NewSession,
            AppDestination.SessionSettingsLibrary,
            AppDestination.SessionSettingsGraph,
            AppDestination.SessionSettingsPersona,
            AppDestination.SessionSettingsPersonalization -> "⋮"
            else -> null
        },
        onActionClick = onStatusClick,
        actions = {
            if (destination == AppDestination.Sessions) {
                ReverseTutorActionButton(
                    label = "挑战",
                    onClick = onChallengeClick,
                    tone = ReverseTutorActionTone.Quiet
                )
                ReverseTutorActionButton(
                    label = "新建",
                    onClick = onNewSessionClick
                )
            }
        }
    )
}

@Composable
private fun AppDrawer(
    current: AppDestination,
    onDestinationClick: (AppDestination) -> Unit,
    onImportExportClick: () -> Unit
) {
    val spacing = ReverseTutorDesign.spacing
    val selectedDestination = when (current) {
        AppDestination.LlmConfiguration,
        AppDestination.ImportExport,
        AppDestination.About,
        AppDestination.TokenUsage,
        AppDestination.Update -> AppDestination.Settings
        AppDestination.GlobalSearch,
        AppDestination.PublicArticle -> AppDestination.Sessions
        else -> current
    }

    ModalDrawerSheet(
        modifier = Modifier.width(304.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space1)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.space5, vertical = spacing.space3),
                verticalArrangement = Arrangement.spacedBy(spacing.space2)
            ) {
                Text(
                    text = "反转家教",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "默认空间",
                        modifier = Modifier.padding(horizontal = spacing.space3, vertical = spacing.space2),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            AppDestination.drawerItems.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(if (item.enabled) item.label else "${item.label}（暂未开放）") },
                    selected = item.enabled && item.destination == selectedDestination,
                    onClick = {
                        if (item.enabled) {
                            onDestinationClick(item.destination)
                        }
                    },
                    modifier = Modifier.padding(horizontal = spacing.space3),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.space5, vertical = spacing.space3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "数据管理",
                        style = MaterialTheme.typography.labelMedium
                    )
                    TextButton(onClick = onImportExportClick) {
                        Text("导入/导出")
                    }
                }
            }
        }
    }
}

private fun AppDestination.usesDrawerNavigation(): Boolean =
    AppDestination.drawerItems.any { it.enabled && it.destination == this }

private fun AppDestination.ownsInContentTopBar(): Boolean =
    workspacePage != null ||
        this == AppDestination.Chat ||
        this == AppDestination.NewSession ||
        this == AppDestination.Settings ||
        this == AppDestination.LlmConfiguration ||
        this == AppDestination.GlobalSearch ||
        this == AppDestination.PublicArticle ||
        this == AppDestination.ImportExport ||
        this == AppDestination.About ||
        this == AppDestination.TokenUsage ||
        this == AppDestination.Update ||
        this == AppDestination.SessionSettingsLibrary ||
        this == AppDestination.SessionSettingsGraph ||
        this == AppDestination.SessionSettingsPersona ||
        this == AppDestination.SessionSettingsPersonalization

@Composable
private fun DestinationContent(
    destination: AppDestination,
    hybridAppGraph: HybridAppGraph,
    appPreferences: AppPreferences,
    sessionRepository: SessionRepository,
    messageRepository: MessageRepository,
    llmProfileRepository: LlmProfileRepository,
    chatGenerationRepository: ChatGenerationRepository,
    backgroundGenerationRepository: BackgroundGenerationRepository?,
    sourceRepository: SourceRepository,
    memoryRepository: MemoryRepository,
    graphRepository: GraphRepository,
    initialImportText: String?,
    initialImportFileName: String?,
    activeSessionId: String?,
    activeSessionTitle: String?,
    activeArticleSlug: String,
    challengeRuntimeState: ChallengeRuntimeState,
    challengeProgress: Int,
    challengeTotal: Int,
    weeklyDashboardState: WeeklyDashboardUiState,
    onComposerFocusChanged: (Boolean) -> Unit,
    onGraphInteractionChanged: (Boolean) -> Unit,
    onChallengeExitBoundaryChanged: (Boolean) -> Unit,
    onWorkspaceChromeObscuredChanged: (WorkspacePage, Boolean) -> Unit,
    onOpenChat: () -> Unit,
    onOpenSession: (com.reversetutor.feature.chat.SessionListItem) -> Unit,
    onOpenContextHub: () -> Unit,
    onOpenGlobalGraph: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenChallenge: () -> Unit,
    onChallengeJoined: () -> Unit,
    onChallengeRetry: () -> Unit,
    onOpenNewSession: () -> Unit,
    onOpenPublicArticle: (String) -> Unit,
    onOpenSearchTarget: (SearchTarget) -> Unit,
    onSessionCreated: (com.reversetutor.feature.chat.SessionListItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImportExport: () -> Unit,
    onOpenAbout: () -> Unit,
    onNavigateDestination: (AppDestination) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var llmProfiles by remember { mutableStateOf(emptyList<LlmProfile>()) }
    var pendingSourceImport by remember { mutableStateOf<SourceImportInput?>(null) }
    var pendingChatImageDraft by remember { mutableStateOf<ChatImageDraft?>(null) }
    var pendingChatEvidenceTarget by remember { mutableStateOf<String?>(null) }
    var pendingSourceEvidenceTarget by remember { mutableStateOf<String?>(null) }
    var weeklySessions by remember { mutableStateOf(emptyList<com.reversetutor.core.model.TutorSession>()) }
    val sourceFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val mimeType = context.contentResolver.getType(uri)
                pendingSourceImport = buildSourceImportInput(
                    requestId = System.currentTimeMillis(),
                    fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "selected-source",
                    mimeType = mimeType,
                    uri = uri.toString(),
                    readText = {
                        context.contentResolver.openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use { readSourceTextWithinLimit(it) }
                    }
                )
            }
        }
    }
    val chatImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                context.tryPersistReadPermission(uri)
                val requestId = System.currentTimeMillis()
                val mimeType = context.contentResolver.getType(uri)
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "selected-image"
                val import = sourceRepository.importSource(
                    input = SourceImportInput(
                        requestId = requestId,
                        fileName = fileName,
                        mimeType = mimeType,
                        uri = uri.toString(),
                        text = null
                    ),
                    nowEpochMillis = requestId
                )
                pendingChatImageDraft = ChatImageDraft(
                    requestId = requestId,
                    name = fileName,
                    mimeType = mimeType,
                    uri = uri.toString(),
                    sourceId = import.source.id
                )
            }
        }
    }
    val llmProfileState = LlmProfileSettingsUiState.from(
        profiles = llmProfiles,
        presets = LlmProviderPreset.defaults,
        connectionResult = null
    )
    LaunchedEffect(destination) {
        if (destination == AppDestination.Settings) {
            llmProfiles = llmProfileRepository.listProfiles()
        }
        if (destination == AppDestination.WeeklyDashboard) {
            weeklySessions = sessionRepository.listSessions().filterNot { it.archived }
        }
    }

    val weeklySessionOptions = remember(weeklySessions) {
        val activeThreshold = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1_000L
        weeklySessions.map { session ->
            FormalWeeklySessionOption(
                id = session.id,
                title = session.title,
                detail = if (session.pinned) "学习模式 · 已置顶" else "学习模式",
                activeThisWeek = session.updatedAtEpochMillis >= activeThreshold
            )
        }
    }

    ReverseTutorScreenSurface {
        if (destination == AppDestination.Sessions) {
            SessionsRoute(
                sessionRepository = sessionRepository,
                contentRepository = hybridAppGraph.online?.contentRepository,
                avatarVisible = appPreferences.globalAvatarVisible,
                challengeJoined = challengeRuntimeState.joined,
                challengeProgress = challengeProgress,
                challengeTotal = challengeTotal,
                onOpenSession = onOpenSession,
                onNewSession = onOpenNewSession,
                onOpenChallenge = onOpenChallenge,
                onOpenPublicContent = { content ->
                    if (content.canOpen) onOpenPublicArticle(content.slug)
                },
                onOpenWeekly = { onNavigateDestination(AppDestination.WeeklyDashboard) },
                showSpatialIndicator = false,
                onWorkspaceChromeObscuredChanged = { obscured ->
                    onWorkspaceChromeObscuredChanged(WorkspacePage.SessionHome, obscured)
                }
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.NewSession) {
            FigmaNewSessionRoute(
                sessionRepository = sessionRepository,
                onCreated = onSessionCreated,
                onBack = onOpenSessions
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.Challenge) {
            ChallengeRoute(
                joined = challengeRuntimeState.joined,
                progress = challengeProgress,
                total = challengeTotal,
                onBack = onOpenSessions,
                onJoin = onChallengeJoined,
                runtimeState = challengeRuntimeState,
                onRetry = onChallengeRetry,
                onExitBoundaryChanged = onChallengeExitBoundaryChanged
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.Chat && activeSessionId != null && activeSessionTitle != null) {
            ChatRoute(
                messageRepository = messageRepository,
                chatGenerationRepository = chatGenerationRepository,
                backgroundGenerationRepository = backgroundGenerationRepository,
                memoryRepository = memoryRepository,
                sourceRepository = sourceRepository,
                sessionId = activeSessionId,
                sessionTitle = activeSessionTitle,
                pendingImageDraft = pendingChatImageDraft,
                evidenceTargetMessageId = pendingChatEvidenceTarget,
                onPickImage = {
                    chatImageLauncher.launch(arrayOf("image/*"))
                },
                onImageDraftConsumed = {
                    pendingChatImageDraft = null
                },
                onBackgroundGenerationQueued = { jobId ->
                    BackgroundGenerationWorker.enqueue(context, jobId)
                },
                onComposerFocusChanged = onComposerFocusChanged,
                onOpenContextHub = onOpenContextHub,
                onBack = onOpenSessions
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.WeeklyDashboard) {
            FormalWeeklyDashboardScreen(
                dashboardState = weeklyDashboardState,
                sessionOptions = weeklySessionOptions,
                selectedSessionIds = weeklySessionOptions
                    .filter { it.activeThisWeek }
                    .take(3)
                    .mapTo(linkedSetOf()) { it.id },
                onOpenSession = { sessionId ->
                    weeklySessions
                        .firstOrNull { it.id == sessionId }
                        ?.toSessionListItem(appPreferences.globalAvatarVisible)
                        ?.let(onOpenSession)
                },
                onOpenQuestion = { onOpenGlobalGraph() },
                onQuickSwitchModel = onOpenSettings,
                showSpatialIndicator = false
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.Community) {
            CommunityRoute(onBack = onOpenGlobalGraph)
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.SessionSettingsGraph) {
            SessionWorldTreeRoute(
                graphRepository = graphRepository,
                sessionId = activeSessionId,
                sessionTitle = activeSessionTitle ?: "当前会话",
                onBack = onOpenChat,
                onGraphInteractionChanged = onGraphInteractionChanged
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.SessionSettingsLibrary ||
            destination == AppDestination.SessionSettingsPersona ||
            destination == AppDestination.SessionSettingsPersonalization
        ) {
            SessionSettingsRoute(
                destination = destination,
                sessionTitle = activeSessionTitle ?: "宏观经济学基础",
                onSelectDestination = onNavigateDestination,
                onOpenBrain = { onNavigateDestination(AppDestination.GlobalGraph) },
                onBack = onOpenChat,
                onPickSource = {
                    sourceFileLauncher.launch(
                        arrayOf("text/*", "application/pdf", "image/*", "*/*")
                    )
                }
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.GlobalGraph) {
            GlobalGraphRoute(
                graphRepository = graphRepository,
                onOpenChat = onOpenChat,
                onOpenSources = onOpenSources,
                onOpenSettings = onOpenSettings,
                onGraphInteractionChanged = onGraphInteractionChanged,
                onWorkspaceChromeObscuredChanged = { obscured ->
                    onWorkspaceChromeObscuredChanged(WorkspacePage.GlobalGraph, obscured)
                }
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.Sources) {
            SourcesRoute(
                sourceRepository = sourceRepository,
                pendingImport = pendingSourceImport,
                highlightedSourceId = pendingSourceEvidenceTarget,
                onPickSource = {
                    sourceFileLauncher.launch(
                        arrayOf(
                            "text/plain",
                            "text/markdown",
                            "text/html",
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            "application/epub+zip",
                            "image/*",
                            "*/*"
                        )
                    )
                }
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.GlobalSearch) {
            FormalGlobalSearchRoute(
                searchRepository = hybridAppGraph.globalSearchRepository,
                onBack = onOpenSessions,
                onTargetSelected = onOpenSearchTarget
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.PublicArticle) {
            FormalPublicArticleRoute(
                contentRepository = hybridAppGraph.online?.contentRepository,
                slug = activeArticleSlug,
                onBack = onOpenSessions
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.Settings) {
            FormalSettingsScreen(
                llmProfileState = llmProfileState,
                onBack = onOpenSessions,
                onOpenLlmConfiguration = {
                    onNavigateDestination(AppDestination.LlmConfiguration)
                },
                onOpenStorage = onOpenAbout,
                onOpenImportExport = onOpenImportExport,
                onOpenAbout = onOpenAbout
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.LlmConfiguration) {
            FormalLlmConfigurationScreen(
                state = llmProfileState,
                onBack = { onNavigateDestination(AppDestination.Settings) },
                onActivateProfile = { profileId ->
                    scope.launch {
                        llmProfileRepository.activateProfile(profileId, System.currentTimeMillis())
                        llmProfiles = llmProfileRepository.listProfiles()
                    }
                },
                onTestProfile = {}
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.About) {
            FormalDiagnosticsRoute(
                hybridAppGraph = hybridAppGraph,
                onBack = { onNavigateDestination(AppDestination.Settings) }
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.ImportExport) {
            FormalImportExportRoute(
                hybridAppGraph = hybridAppGraph,
                onBack = { onNavigateDestination(AppDestination.Settings) },
                initialImportText = initialImportText,
                initialImportFileName = initialImportFileName
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.TokenUsage) {
            FormalTokenRoute(
                hybridAppGraph = hybridAppGraph,
                onBack = { onNavigateDestination(AppDestination.Settings) },
                onOpenSession = { sessionId ->
                    scope.launch {
                        sessionRepository.getSession(sessionId)?.toSessionListItem(appPreferences.globalAvatarVisible)?.let(onOpenSession)
                    }
                }
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.Update) {
            FormalUpdateRoute(
                hybridAppGraph = hybridAppGraph,
                onBack = { onNavigateDestination(AppDestination.Settings) }
            )
            return@ReverseTutorScreenSurface
        }
        val spacing = ReverseTutorDesign.spacing
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.space6),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Reverse Tutor",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displaySmall
            )
            Spacer(modifier = Modifier.height(spacing.space2))
            Text(
                text = if (destination == AppDestination.Chat && activeSessionTitle != null) {
                    activeSessionTitle
                } else {
                    destination.title
                },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(spacing.space3))
            ReverseTutorStatusStrip(
                title = "预览占位",
                message = "当前页面仍保留在 native 壳内，后续会继续补齐对应界面。",
                tone = ReverseTutorStatusTone.Info
            )
            Spacer(modifier = Modifier.height(spacing.space6))
            PreviewActions(
                destination = destination,
                onOpenChat = onOpenChat,
                onOpenContextHub = onOpenContextHub,
                onOpenSources = onOpenSources,
                onOpenSettings = onOpenSettings,
                onOpenImportExport = onOpenImportExport,
                onOpenAbout = onOpenAbout
            )
        }
    }
}

private fun Context.tryPersistReadPermission(uri: android.net.Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

@Composable
private fun FirstLaunchImportPromptDialog(
    state: FirstLaunchImportPromptUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ReverseTutorConfirmationDialog(
        title = state.title,
        body = state.body,
        confirmLabel = state.confirmLabel,
        dismissLabel = state.dismissLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        tone = ReverseTutorStatusTone.Info
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PreviewActions(
    destination: AppDestination,
    onOpenChat: () -> Unit,
    onOpenContextHub: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImportExport: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val spacing = ReverseTutorDesign.spacing

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.space2),
        verticalArrangement = Arrangement.spacedBy(spacing.space2)
    ) {
        if (destination != AppDestination.Chat) {
            ReverseTutorActionButton(label = "打开聊天", onClick = onOpenChat)
        }
        if (destination == AppDestination.Chat) {
            ReverseTutorActionButton(label = "打开脉络", onClick = onOpenContextHub)
        }
        if (destination != AppDestination.Sources) {
            ReverseTutorActionButton(
                label = "资料",
                onClick = onOpenSources,
                tone = ReverseTutorActionTone.Quiet
            )
        }
        if (destination != AppDestination.Settings) {
            ReverseTutorActionButton(
                label = "设置",
                onClick = onOpenSettings,
                tone = ReverseTutorActionTone.Quiet
            )
        }
        if (destination != AppDestination.ImportExport) {
            ReverseTutorActionButton(
                label = "导入与导出",
                onClick = onOpenImportExport,
                tone = ReverseTutorActionTone.Quiet
            )
        }
        if (destination != AppDestination.About) {
            ReverseTutorActionButton(
                label = "诊断",
                onClick = onOpenAbout,
                tone = ReverseTutorActionTone.Quiet
            )
        }
    }
}

@Composable
private fun StatusDialog(
    destination: AppDestination,
    onDismiss: () -> Unit
) {
    ReverseTutorConfirmationDialog(
        title = "预览状态",
        body = "当前页面：${destination.title}",
        confirmLabel = "关闭",
        dismissLabel = "返回",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        tone = ReverseTutorStatusTone.Info
    )
}
