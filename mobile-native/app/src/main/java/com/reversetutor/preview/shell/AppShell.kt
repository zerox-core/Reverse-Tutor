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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.reversetutor.preview.theme.ReverseTutorDesign
import com.reversetutor.preview.theme.ReverseTutorStatusTone
import com.reversetutor.preview.ui.ReverseTutorActionButton
import com.reversetutor.preview.ui.ReverseTutorActionTone
import com.reversetutor.preview.ui.ReverseTutorConfirmationDialog
import com.reversetutor.preview.ui.ReverseTutorNavigationItem
import com.reversetutor.preview.ui.ReverseTutorNavigationStrip
import com.reversetutor.preview.ui.ReverseTutorScaffold
import com.reversetutor.preview.ui.ReverseTutorScreenSurface
import com.reversetutor.preview.ui.ReverseTutorStatusStrip
import com.reversetutor.preview.ui.ReverseTutorTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

@Composable
fun AppShell(
    appPreferences: AppPreferences = AppPreferences.defaults,
    sessionRepository: SessionRepository,
    messageRepository: MessageRepository,
    llmProfileRepository: LlmProfileRepository,
    chatGenerationRepository: ChatGenerationRepository,
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
    var showFirstLaunchImportPrompt by remember(firstLaunchImportPromptState) {
        mutableStateOf(firstLaunchImportPromptState.shouldShow)
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

    ReverseTutorScaffold(
        topBar = {
            PreviewTopBar(
                destination = navigationState.current,
                onStatusClick = {
                    navigationState = navigationState.openModal(AppModal.Status)
                }
            )
        },
        bottomBar = {
            DestinationStrip(
                current = navigationState.current,
                onDestinationClick = {
                    navigationState = navigationState.navigate(it)
                }
            )
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
                onOpenSources = {
                    navigationState = navigationState.navigate(AppDestination.Sources)
                },
                onOpenSessions = {
                    navigationState = navigationState.navigate(AppDestination.Sessions)
                },
                onOpenSettings = {
                    navigationState = navigationState.navigate(AppDestination.Settings)
                },
                onOpenAbout = {
                    navigationState = navigationState.navigate(AppDestination.About)
                }
            )
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
    onStatusClick: () -> Unit
) {
    ReverseTutorTopAppBar(
        title = destination.title,
        subtitle = destination.status,
        actionLabel = "Status",
        onActionClick = onStatusClick
    )
}

@Composable
private fun DestinationStrip(
    current: AppDestination,
    onDestinationClick: (AppDestination) -> Unit
) {
    val items = AppDestination.entries.map { destination ->
        ReverseTutorNavigationItem(
            key = destination.route,
            label = destination.title,
            contentDescription = destination.title
        )
    }
    ReverseTutorNavigationStrip(
        items = items,
        selectedKey = current.route,
        onItemSelected = { key ->
            AppDestination.entries.firstOrNull { it.route == key }?.let(onDestinationClick)
        }
    )
}

@Composable
private fun DestinationContent(
    destination: AppDestination,
    appPreferences: AppPreferences,
    sessionRepository: SessionRepository,
    messageRepository: MessageRepository,
    llmProfileRepository: LlmProfileRepository,
    chatGenerationRepository: ChatGenerationRepository,
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
    onOpenChat: () -> Unit,
    onOpenSession: (com.reversetutor.feature.chat.SessionListItem) -> Unit,
    onOpenContextHub: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSettings: () -> Unit,
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
                onOpenSession = onOpenSession
            )
            return@ReverseTutorScreenSurface
        }
        if (destination == AppDestination.Chat && activeSessionId != null && activeSessionTitle != null) {
            ChatRoute(
                messageRepository = messageRepository,
                chatGenerationRepository = chatGenerationRepository,
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
                onOpenSettings = onOpenSettings
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
                        wipeStatusLabel = "Local data wiped. Default preview restored. Secrets removed: ${result.deletedSecretRefCount}."
                    }
                },
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
                        exportState = ExportPipelineUiState.unavailable("Open a session before exporting the current session.")
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
                        message = "Preset export is represented in the protocol, but this preview has no selected preset owner yet.",
                        kindLabel = "Preset"
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
                title = "Internal preview placeholder",
                message = "This surface keeps the native product shell reachable while feature UI lands.",
                tone = ReverseTutorStatusTone.Info
            )
            Spacer(modifier = Modifier.height(spacing.space6))
            PreviewActions(
                destination = destination,
                onOpenChat = onOpenChat,
                onOpenContextHub = onOpenContextHub,
                onOpenSources = onOpenSources,
                onOpenSettings = onOpenSettings,
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
    startActivity(Intent.createChooser(intent, "Share export"))
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
    onOpenAbout: () -> Unit
) {
    val spacing = ReverseTutorDesign.spacing

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.space2),
        verticalArrangement = Arrangement.spacedBy(spacing.space2)
    ) {
        if (destination != AppDestination.Chat) {
            ReverseTutorActionButton(label = "Open chat", onClick = onOpenChat)
        }
        if (destination == AppDestination.Chat) {
            ReverseTutorActionButton(label = "Open context hub", onClick = onOpenContextHub)
        }
        if (destination != AppDestination.Sources) {
            ReverseTutorActionButton(
                label = "Sources",
                onClick = onOpenSources,
                tone = ReverseTutorActionTone.Quiet
            )
        }
        if (destination != AppDestination.Settings) {
            ReverseTutorActionButton(
                label = "Settings",
                onClick = onOpenSettings,
                tone = ReverseTutorActionTone.Quiet
            )
        }
        if (destination != AppDestination.About) {
            ReverseTutorActionButton(
                label = "About",
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
        title = "Preview status",
        body = "Current route: ${destination.route}",
        confirmLabel = "Close",
        dismissLabel = "Dismiss",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        tone = ReverseTutorStatusTone.Info
    )
}
