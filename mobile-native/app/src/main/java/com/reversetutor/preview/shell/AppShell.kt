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
import com.reversetutor.core.data.migration.NativeExportRepository
import com.reversetutor.core.data.migration.NativeImportRepository
import com.reversetutor.core.data.migration.NativeImportMode
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.data.sources.SourceImportInput
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.data.preferences.AppPreferences
import com.reversetutor.core.data.wipe.LocalDataWipeRepository
import com.reversetutor.core.llm.LlmConnectionResult
import com.reversetutor.core.llm.LlmProviderPreset
import com.reversetutor.core.llm.MockLlmConnectionTester
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.feature.chat.ChatRoute
import com.reversetutor.feature.chat.ChatImageDraft
import com.reversetutor.feature.chat.NewSessionRoute
import com.reversetutor.feature.chat.SessionsRoute
import com.reversetutor.feature.memory.ContextHubRoute
import com.reversetutor.feature.memory.GlobalGraphRoute
import com.reversetutor.feature.sources.SourcesRoute
import com.reversetutor.feature.settings.AboutDiagnosticsScreen
import com.reversetutor.feature.settings.ExportPipelineUiState
import com.reversetutor.feature.settings.FirstLaunchImportPromptUiState
import com.reversetutor.feature.settings.ImportExportScreen
import com.reversetutor.feature.settings.ImportPipelineUiState
import com.reversetutor.feature.settings.LocalDataWipeUiState
import com.reversetutor.feature.settings.LlmProfileSettingsUiState
import com.reversetutor.feature.settings.NativeDiagnosticsInfo
import com.reversetutor.feature.settings.SettingsFoundationScreen
import com.reversetutor.feature.settings.SettingsUiState
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun AppShell(
    appPreferences: AppPreferences = AppPreferences.defaults,
    sessionRepository: SessionRepository,
    messageRepository: MessageRepository,
    llmProfileRepository: LlmProfileRepository,
    chatGenerationRepository: ChatGenerationRepository,
    backgroundGenerationRepository: BackgroundGenerationRepository? = null,
    sourceRepository: SourceRepository,
    memoryRepository: MemoryRepository,
    graphRepository: GraphRepository,
    localDataWipeRepository: LocalDataWipeRepository,
    nativeImportRepository: NativeImportRepository,
    nativeExportRepository: NativeExportRepository,
    firstLaunchImportPromptState: FirstLaunchImportPromptUiState = FirstLaunchImportPromptUiState.preview(),
    initialImportText: String? = null,
    initialImportFileName: String? = null,
    onExitRequested: () -> Unit
) {
    var navigationState by remember { mutableStateOf(AppNavigationState()) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var activeSessionTitle by remember { mutableStateOf<String?>(null) }
    var challengeJoined by remember { mutableStateOf(false) }
    var showFirstLaunchImportPrompt by remember(firstLaunchImportPromptState) {
        mutableStateOf(firstLaunchImportPromptState.shouldShow)
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

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
                if (navigationState.current != AppDestination.Challenge &&
                    navigationState.current != AppDestination.Sessions
                ) {
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
                            navigationState = navigationState.openModal(AppModal.Status)
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
                DestinationContent(
                    destination = navigationState.current,
                    appPreferences = appPreferences,
                    sessionRepository = sessionRepository,
                    messageRepository = messageRepository,
                    llmProfileRepository = llmProfileRepository,
                    chatGenerationRepository = chatGenerationRepository,
                    backgroundGenerationRepository = backgroundGenerationRepository,
                    sourceRepository = sourceRepository,
                    memoryRepository = memoryRepository,
                    graphRepository = graphRepository,
                    localDataWipeRepository = localDataWipeRepository,
                    nativeImportRepository = nativeImportRepository,
                    nativeExportRepository = nativeExportRepository,
                    initialImportText = initialImportText,
                    initialImportFileName = initialImportFileName,
                    activeSessionId = activeSessionId,
                    activeSessionTitle = activeSessionTitle,
                    challengeJoined = challengeJoined,
                    onOpenChat = {
                        navigationState = navigationState.navigate(AppDestination.Chat)
                    },
                    onOpenSession = { session ->
                        activeSessionId = session.id
                        activeSessionTitle = session.title
                        navigationState = navigationState.navigate(AppDestination.Chat)
                    },
                    onOpenContextHub = {
                        navigationState = navigationState.navigate(AppDestination.ContextHub)
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
                        challengeJoined = true
                        navigationState = navigationState.navigate(AppDestination.Sessions)
                    },
                    onOpenNewSession = {
                        navigationState = navigationState.navigate(AppDestination.NewSession)
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
                    }
                )
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
        actionLabel = if (destination == AppDestination.Sessions) null else "状态",
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
        AppDestination.GlobalGraph -> AppDestination.ContextHub
        AppDestination.ImportExport,
        AppDestination.About -> AppDestination.Settings
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

@Composable
private fun DestinationContent(
    destination: AppDestination,
    appPreferences: AppPreferences,
    sessionRepository: SessionRepository,
    messageRepository: MessageRepository,
    llmProfileRepository: LlmProfileRepository,
    chatGenerationRepository: ChatGenerationRepository,
    backgroundGenerationRepository: BackgroundGenerationRepository?,
    sourceRepository: SourceRepository,
    memoryRepository: MemoryRepository,
    graphRepository: GraphRepository,
    localDataWipeRepository: LocalDataWipeRepository,
    nativeImportRepository: NativeImportRepository,
    nativeExportRepository: NativeExportRepository,
    initialImportText: String?,
    initialImportFileName: String?,
    activeSessionId: String?,
    activeSessionTitle: String?,
    challengeJoined: Boolean,
    onOpenChat: () -> Unit,
    onOpenSession: (com.reversetutor.feature.chat.SessionListItem) -> Unit,
    onOpenContextHub: () -> Unit,
    onOpenGlobalGraph: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenChallenge: () -> Unit,
    onChallengeJoined: () -> Unit,
    onOpenNewSession: () -> Unit,
    onSessionCreated: (com.reversetutor.feature.chat.SessionListItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImportExport: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val diagnostics = remember {
        NativeDiagnosticsInfo.preview(
            packageName = "com.reversetutor.preview",
            versionName = "0.1.0-native-preview",
            versionCode = 1
        )
    }
    val settingsState = remember(diagnostics) {
        SettingsUiState.from(
            preferences = appPreferences,
            diagnostics = diagnostics
        )
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val connectionTester = remember { MockLlmConnectionTester() }
    var llmProfiles by remember { mutableStateOf(emptyList<LlmProfile>()) }
    var llmConnectionResult by remember { mutableStateOf<LlmConnectionResult?>(null) }
    var wipeStatusLabel by remember { mutableStateOf<String?>(null) }
    var importText by remember { mutableStateOf("") }
    var importFileName by remember { mutableStateOf("pasted-import.json") }
    var pendingSourceImport by remember { mutableStateOf<SourceImportInput?>(null) }
    var pendingChatImageDraft by remember { mutableStateOf<ChatImageDraft?>(null) }
    var pendingChatEvidenceTarget by remember { mutableStateOf<String?>(null) }
    var pendingSourceEvidenceTarget by remember { mutableStateOf<String?>(null) }
    var selectedImportMode by remember { mutableStateOf(NativeImportMode.Append) }
    var importState by remember { mutableStateOf(ImportPipelineUiState.idle(selectedImportMode)) }
    var exportState by remember { mutableStateOf(ExportPipelineUiState.idle()) }
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val fileText = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                importText = fileText
                importFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "selected-import.json"
                importState = ImportPipelineUiState.idle(selectedImportMode).copy(selectedFileName = importFileName)
            }
        }
    }
    val exportSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = exportState.json
        if (uri != null && !json.isNullOrBlank()) {
            scope.launch {
                context.contentResolver.openOutputStream(uri)
                    ?.bufferedWriter(Charsets.UTF_8)
                    ?.use { it.write(json) }
                exportState = ExportPipelineUiState.saved(exportState.targetFileName)
            }
        }
    }
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
        connectionResult = llmConnectionResult
    )
    LaunchedEffect(initialImportText, initialImportFileName) {
        if (!initialImportText.isNullOrBlank()) {
            importText = initialImportText
            importFileName = initialImportFileName ?: "received-import.json"
            importState = ImportPipelineUiState.idle(selectedImportMode).copy(selectedFileName = importFileName)
        }
    }
    LaunchedEffect(destination) {
        if (destination == AppDestination.Settings) {
            llmProfiles = llmProfileRepository.listProfiles()
        }
    }

    ReverseTutorScreenSurface {
        if (destination == AppDestination.Sessions) {
            SessionsRoute(
                sessionRepository = sessionRepository,
                avatarVisible = appPreferences.globalAvatarVisible,
                challengeJoined = challengeJoined,
                onOpenSession = onOpenSession,
                onNewSession = onOpenNewSession,
                onOpenChallenge = onOpenChallenge
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.NewSession) {
            NewSessionRoute(
                sessionRepository = sessionRepository,
                onCreated = onSessionCreated,
                onCancel = onOpenSessions
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.Challenge) {
            ChallengeRoute(
                joined = challengeJoined,
                onBack = onOpenSessions,
                onJoin = onChallengeJoined
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
                onOpenContextHub = onOpenContextHub
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.ContextHub) {
            ContextHubRoute(
                memoryRepository = memoryRepository,
                graphRepository = graphRepository,
                sessionId = activeSessionId,
                sessionTitle = activeSessionTitle,
                onOpenChat = onOpenChat,
                onOpenSources = onOpenSources,
                onOpenChatEvidence = { messageId ->
                    pendingChatEvidenceTarget = messageId
                    onOpenChat()
                },
                onOpenSourceEvidence = { sourceId ->
                    pendingSourceEvidenceTarget = sourceId
                    onOpenSources()
                },
                onOpenSettings = onOpenSettings,
                onOpenGlobalGraph = onOpenGlobalGraph
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.GlobalGraph) {
            GlobalGraphRoute(
                graphRepository = graphRepository,
                onOpenChat = onOpenChat,
                onOpenSources = onOpenSources,
                onOpenSettings = onOpenSettings
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
        if (destination == AppDestination.Settings) {
            SettingsFoundationScreen(
                state = settingsState,
                onOpenAbout = onOpenAbout,
                llmProfileState = llmProfileState,
                onSaveLlmProfile = { input ->
                    scope.launch {
                        llmProfileRepository.saveProfile(
                            input = input,
                            nowEpochMillis = System.currentTimeMillis()
                        )
                        llmProfiles = llmProfileRepository.listProfiles()
                        llmConnectionResult = null
                    }
                },
                onActivateLlmProfile = { profileId ->
                    scope.launch {
                        llmProfileRepository.activateProfile(
                            profileId = profileId,
                            nowEpochMillis = System.currentTimeMillis()
                        )
                        llmProfiles = llmProfileRepository.listProfiles()
                    }
                },
                onDeleteLlmProfile = { profileId ->
                    scope.launch {
                        llmProfileRepository.deleteProfile(profileId)
                        llmProfiles = llmProfileRepository.listProfiles()
                    }
                },
                onTestLlmProfile = { profileId ->
                    val profile = llmProfiles.firstOrNull { it.id == profileId }
                    if (profile != null) {
                        llmConnectionResult = connectionTester.testConnection(profile)
                    }
                },
                localDataWipeState = LocalDataWipeUiState(statusLabel = wipeStatusLabel),
                onWipeLocalData = {
                    scope.launch {
                        val result = localDataWipeRepository.wipeLocalData(System.currentTimeMillis())
                        llmProfiles = llmProfileRepository.listProfiles()
                        llmConnectionResult = null
                        wipeStatusLabel = "本地数据已清空。已恢复默认预览，并移除密钥引用 ${result.deletedSecretRefCount} 个。"
                    }
                },
                onOpenImportExport = onOpenImportExport,
                onReturnToSessions = {
                    onOpenSessions()
                }
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.About) {
            AboutDiagnosticsScreen(diagnostics = diagnostics)
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.ImportExport) {
            ImportExportScreen(
                state = importState,
                exportState = exportState,
                importText = importText,
                canExportCurrentSession = activeSessionId != null,
                onImportTextChange = {
                    importText = it
                    importFileName = "pasted-import.json"
                    importState = ImportPipelineUiState.idle(selectedImportMode).copy(selectedFileName = importFileName)
                },
                onImportModeChange = { mode ->
                    selectedImportMode = mode
                    importState = ImportPipelineUiState.idle(mode).copy(selectedFileName = importFileName)
                },
                onPickFile = {
                    importFileLauncher.launch(arrayOf("application/json", "text/*"))
                },
                onDryRun = {
                    scope.launch {
                        importState = ImportPipelineUiState.from(
                            nativeImportRepository.dryRun(
                                json = importText,
                                sourceFileName = importFileName,
                                nowEpochMillis = System.currentTimeMillis(),
                                mode = selectedImportMode
                            )
                        )
                    }
                },
                onImport = { overwriteConfirmed ->
                    scope.launch {
                        val result = nativeImportRepository.importJson(
                            json = importText,
                            sourceFileName = importFileName,
                            nowEpochMillis = System.currentTimeMillis(),
                            mode = selectedImportMode,
                            overwriteConfirmed = overwriteConfirmed
                        )
                        importState = ImportPipelineUiState.from(result)
                    }
                },
                onExportCurrentSession = {
                    val sessionId = activeSessionId
                    if (sessionId == null) {
                        exportState = ExportPipelineUiState.unavailable("请先打开一个会话，再导出当前会话。")
                    } else {
                        scope.launch {
                            exportState = ExportPipelineUiState.from(
                                nativeExportRepository.currentSession(
                                    sessionId = sessionId,
                                    createdAt = System.currentTimeMillis().toExportTimestamp()
                                )
                            )
                        }
                    }
                },
                onExportGraphSnapshot = {
                    scope.launch {
                        exportState = ExportPipelineUiState.from(
                            nativeExportRepository.graphSnapshot(
                                createdAt = System.currentTimeMillis().toExportTimestamp()
                            )
                        )
                    }
                },
                onExportFullBackup = {
                    scope.launch {
                        exportState = ExportPipelineUiState.from(
                            nativeExportRepository.fullBackup(
                                createdAt = System.currentTimeMillis().toExportTimestamp()
                            )
                        )
                    }
                },
                onExportPreset = {
                    exportState = ExportPipelineUiState.unavailable(
                        message = "协议已支持预设导出，但当前预览还没有选择预设归属。",
                        kindLabel = "预设"
                    )
                },
                onShareExport = {
                    val json = exportState.json
                    if (!json.isNullOrBlank()) {
                        context.shareExportText(
                            fileName = exportState.targetFileName ?: "reverse-tutor-export.json",
                            json = json
                        )
                    }
                },
                onSaveExport = {
                    exportSaveLauncher.launch(exportState.targetFileName ?: "reverse-tutor-export.json")
                }
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

private fun Context.shareExportText(
    fileName: String,
    json: String
) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_TITLE, fileName)
        .putExtra(Intent.EXTRA_SUBJECT, fileName)
        .putExtra(Intent.EXTRA_TEXT, json)
    startActivity(Intent.createChooser(intent, "共享导出"))
}

private fun Context.tryPersistReadPermission(uri: android.net.Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

private fun Long.toExportTimestamp(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(this))
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
