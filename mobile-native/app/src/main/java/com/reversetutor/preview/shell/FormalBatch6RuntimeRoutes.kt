package com.reversetutor.preview.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.reversetutor.core.data.migration.NativeExportResult
import com.reversetutor.core.data.migration.NativeImportMode
import com.reversetutor.core.data.migration.NativeImportResult
import com.reversetutor.core.data.migration.NativeImportStatus
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.domain.ReleaseMetadata
import com.reversetutor.core.model.SourceParserStatus
import com.reversetutor.core.model.ErrorLogOrigin
import com.reversetutor.core.model.SyncConflict
import com.reversetutor.core.model.TokenUsageRecord
import com.reversetutor.core.model.TutorSession
import com.reversetutor.feature.settings.FormalAvailableUpdate
import com.reversetutor.feature.settings.FormalDiagnosticEvent
import com.reversetutor.feature.settings.FormalDiagnosticIcon
import com.reversetutor.feature.settings.FormalDiagnosticReportScreen
import com.reversetutor.feature.settings.FormalDiagnosticReportUiState
import com.reversetutor.feature.settings.FormalDiagnosticStatusRow
import com.reversetutor.feature.settings.FormalDiagnosticTone
import com.reversetutor.feature.settings.FormalDiagnosticsOverviewScreen
import com.reversetutor.feature.settings.FormalDiagnosticsUiState
import com.reversetutor.feature.settings.FormalExportSessionItem
import com.reversetutor.feature.settings.FormalImportExportScreen
import com.reversetutor.feature.settings.FormalImportExportUiState
import com.reversetutor.feature.settings.FormalImportIssue
import com.reversetutor.feature.settings.FormalImportIssueSeverity
import com.reversetutor.feature.settings.FormalImportMode
import com.reversetutor.feature.settings.FormalImportPreviewScreen
import com.reversetutor.feature.settings.FormalImportPreviewUiState
import com.reversetutor.feature.settings.FormalSessionExportSelectionScreen
import com.reversetutor.feature.settings.FormalSessionExportUiState
import com.reversetutor.feature.settings.FormalStorageSegment
import com.reversetutor.feature.settings.FormalSyncChoiceOption
import com.reversetutor.feature.settings.FormalSyncChoiceUiState
import com.reversetutor.feature.settings.FormalSyncChoiceValue
import com.reversetutor.feature.settings.FormalSyncConflictChoiceScreen
import com.reversetutor.feature.settings.FormalSyncConflictIcon
import com.reversetutor.feature.settings.FormalSyncConflictItem
import com.reversetutor.feature.settings.FormalSyncConflictOverviewScreen
import com.reversetutor.feature.settings.FormalSyncConflictUiState
import com.reversetutor.feature.settings.FormalSyncSource
import com.reversetutor.feature.settings.FormalTokenByModelScreen
import com.reversetutor.feature.settings.FormalTokenByModelUiState
import com.reversetutor.feature.settings.FormalTokenBySessionScreen
import com.reversetutor.feature.settings.FormalTokenBySessionUiState
import com.reversetutor.feature.settings.FormalTokenModelItem
import com.reversetutor.feature.settings.FormalTokenOverviewScreen
import com.reversetutor.feature.settings.FormalTokenOverviewUiState
import com.reversetutor.feature.settings.FormalTokenPeriod
import com.reversetutor.feature.settings.FormalTokenSessionItem
import com.reversetutor.feature.settings.FormalTokenSummary
import com.reversetutor.feature.settings.FormalTokenTrendPoint
import com.reversetutor.feature.settings.FormalTransferRecord
import com.reversetutor.feature.settings.FormalTransferStatus
import com.reversetutor.feature.settings.FormalUpdateScreen
import com.reversetutor.feature.settings.FormalUpdateUiState
import com.reversetutor.preview.wiring.HybridAppGraph
import com.reversetutor.preview.background.GenerationDiagnosticPolicy
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.launch

private const val DefaultSpaceId = SessionRepository.defaultSpaceId
private const val GenerationDiagnosticEventPrefix = "generation-diagnostic-"

private enum class ImportExportPage { Overview, ImportPreview, SessionSelection }
private enum class DiagnosticsPage { Overview, Report }
private enum class TokenPage { Overview, ByModel, BySession }

fun interface FormalSyncConflictResolver {
    suspend fun resolve(conflict: SyncConflict, selectedSource: FormalSyncSource): Boolean
}

@Composable
fun FormalImportExportRoute(
    hybridAppGraph: HybridAppGraph,
    onBack: () -> Unit,
    initialImportText: String? = null,
    initialImportFileName: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember(hybridAppGraph) { FormalBatch6RuntimeCoordinator(hybridAppGraph) }
    var page by remember { mutableStateOf(ImportExportPage.Overview) }
    var overview by remember { mutableStateOf(FormalBatch6RuntimeStateFactory.emptyImportExport()) }
    var recentRecords by remember { mutableStateOf(emptyList<FormalTransferRecord>()) }
    var importDocument by remember { mutableStateOf<RuntimeImportDocument?>(null) }
    var importMode by remember { mutableStateOf(FormalImportMode.Append) }
    var importPreview by remember { mutableStateOf<FormalImportPreviewUiState?>(null) }
    var sessionSelection by remember { mutableStateOf(FormalBatch6RuntimeStateFactory.emptySessionSelection()) }
    var selectedSessionIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingDocument by remember { mutableStateOf<RuntimeOutputDocument?>(null) }

    fun reloadOverview() {
        scope.launch {
            overview = coordinator.importExportOverview(recentRecords)
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val document = pendingDocument
        if (uri != null && document != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(document.bytes) }
            }.onSuccess {
                recentRecords = listOf(
                    FormalTransferRecord(
                        id = "export-${System.currentTimeMillis()}",
                        title = document.recordTitle,
                        subtitle = "${document.fileName} · ${formatBytes(document.bytes.size.toLong())}",
                        status = FormalTransferStatus.Completed
                    )
                ) + recentRecords
                reloadOverview()
                page = ImportExportPage.Overview
            }
        }
        pendingDocument = null
    }

    fun saveExport(result: NativeExportResult, recordTitle: String) {
        val json = result.json
        if (result.isValid && !json.isNullOrBlank()) {
            pendingDocument = RuntimeOutputDocument(
                fileName = result.targetFileName,
                bytes = json.toByteArray(Charsets.UTF_8),
                recordTitle = recordTitle
            )
            saveLauncher.launch(result.targetFileName)
        } else {
            recentRecords = listOf(
                FormalTransferRecord(
                    id = "export-warning-${System.currentTimeMillis()}",
                    title = recordTitle,
                    subtitle = result.errors.firstOrNull() ?: "导出校验未通过",
                    status = FormalTransferStatus.Warning
                )
            ) + recentRecords
            reloadOverview()
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) return@launch
                val document = RuntimeImportDocument(
                    fileName = context.displayName(uri) ?: "selected-import.json",
                    json = bytes.toString(Charsets.UTF_8),
                    sizeBytes = bytes.size.toLong()
                )
                importDocument = document
                val result = coordinator.dryRunImport(document, importMode)
                importPreview = FormalBatch6RuntimeStateFactory.importPreview(document, result, importMode)
                page = ImportExportPage.ImportPreview
            }
        }
    }

    LaunchedEffect(Unit) {
        overview = coordinator.importExportOverview(recentRecords)
    }

    LaunchedEffect(initialImportText, initialImportFileName) {
        val json = initialImportText?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val document = RuntimeImportDocument(
            fileName = initialImportFileName ?: "received-import.json",
            json = json,
            sizeBytes = json.toByteArray(Charsets.UTF_8).size.toLong()
        )
        importDocument = document
        val result = coordinator.dryRunImport(document, importMode)
        importPreview = FormalBatch6RuntimeStateFactory.importPreview(document, result, importMode)
        page = ImportExportPage.ImportPreview
    }

    when (page) {
        ImportExportPage.Overview -> FormalImportExportScreen(
            state = overview,
            onBack = onBack,
            onChooseImportFile = {
                importFileLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
            },
            onSelectSessions = {
                scope.launch {
                    selectedSessionIds = emptySet()
                    sessionSelection = coordinator.sessionExportSelection(selectedSessionIds, "")
                    page = ImportExportPage.SessionSelection
                }
            },
            onExportAllLocalData = {
                scope.launch {
                    saveExport(coordinator.fullBackup(), "完整本地备份")
                }
            },
            onExportGlobalGraph = {
                scope.launch {
                    saveExport(coordinator.graphSnapshot(), "全局图谱导出")
                }
            },
            onOpenRecord = {},
            onOpenAllRecords = {},
            modifier = modifier
        )

        ImportExportPage.ImportPreview -> importPreview?.let { state ->
            FormalImportPreviewScreen(
                state = state,
                onBack = { page = ImportExportPage.Overview },
                onModeSelected = { selected ->
                    importMode = selected
                    val document = importDocument ?: return@FormalImportPreviewScreen
                    scope.launch {
                        val result = coordinator.dryRunImport(document, selected)
                        importPreview = FormalBatch6RuntimeStateFactory.importPreview(document, result, selected)
                    }
                },
                onSelectTargetSpace = {},
                onCancel = { page = ImportExportPage.Overview },
                onStartImport = {
                    val document = importDocument ?: return@FormalImportPreviewScreen
                    scope.launch {
                        val result = coordinator.import(document, importMode)
                        if (result.status == NativeImportStatus.Completed || result.status == NativeImportStatus.Partial) {
                            recentRecords = listOf(FormalBatch6RuntimeStateFactory.importRecord(result)) + recentRecords
                            overview = coordinator.importExportOverview(recentRecords)
                            page = ImportExportPage.Overview
                        } else {
                            importPreview = FormalBatch6RuntimeStateFactory.importPreview(document, result, importMode)
                        }
                    }
                },
                modifier = modifier
            )
        }

        ImportExportPage.SessionSelection -> FormalSessionExportSelectionScreen(
            state = sessionSelection,
            onBack = { page = ImportExportPage.Overview },
            onSearchQueryChange = { query ->
                scope.launch {
                    sessionSelection = coordinator.sessionExportSelection(selectedSessionIds, query)
                }
            },
            onToggleSelectAll = {
                scope.launch {
                    val visible = sessionSelection.sessions
                    selectedSessionIds = if (visible.isNotEmpty() && visible.all { it.selected }) {
                        selectedSessionIds - visible.map { it.id }.toSet()
                    } else {
                        selectedSessionIds + visible.map { it.id }
                    }
                    sessionSelection = coordinator.sessionExportSelection(selectedSessionIds, sessionSelection.searchQuery)
                }
            },
            onToggleSession = { id ->
                scope.launch {
                    selectedSessionIds = if (id in selectedSessionIds) selectedSessionIds - id else selectedSessionIds + id
                    sessionSelection = coordinator.sessionExportSelection(selectedSessionIds, sessionSelection.searchQuery)
                }
            },
            onCancel = { page = ImportExportPage.Overview },
            onExportSelected = {
                val selectedIds = selectedSessionIds.toList()
                scope.launch {
                    val results = selectedIds.map { coordinator.currentSession(it) }
                    val output = FormalBatch6RuntimeStateFactory.sessionExportDocument(results)
                    if (output != null) {
                        pendingDocument = output
                        saveLauncher.launch(output.fileName)
                    }
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun FormalDiagnosticsRoute(
    hybridAppGraph: HybridAppGraph,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember(hybridAppGraph) { FormalBatch6RuntimeCoordinator(hybridAppGraph) }
    var page by remember { mutableStateOf(DiagnosticsPage.Overview) }
    var state by remember(context) {
        mutableStateOf(FormalBatch6RuntimeStateFactory.emptyDiagnostics(context.runtimeAppInfo()))
    }
    var report by remember { mutableStateOf<FormalDiagnosticReportUiState?>(null) }
    var events by remember { mutableStateOf(emptyList<FormalDiagnosticEvent>()) }
    var pendingDocument by remember { mutableStateOf<RuntimeOutputDocument?>(null) }

    fun refresh(addEvent: FormalDiagnosticEvent? = null) {
        scope.launch {
            val generatedEvents = coordinator.generationDiagnosticEvents()
            events = listOfNotNull(addEvent) +
                events.filterNot { it.id.startsWith(GenerationDiagnosticEventPrefix) } +
                generatedEvents
            state = coordinator.diagnostics(context, showWipeConfirmation = false)
        }
    }

    val reportSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val document = pendingDocument
        if (uri != null && document != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(document.bytes) }
            }
        }
        pendingDocument = null
    }

    LaunchedEffect(Unit) {
        state = coordinator.diagnostics(context, showWipeConfirmation = false)
        events = listOf(
            FormalDiagnosticEvent(
                id = "initial-check",
                title = "本地运行检查",
                subtitle = "仓库状态已重新读取",
                statusLabel = "完成",
                warning = false
            )
        ) + coordinator.generationDiagnosticEvents()
    }

    when (page) {
        DiagnosticsPage.Overview -> FormalDiagnosticsOverviewScreen(
            state = state,
            onBack = onBack,
            onCheckNow = {
                refresh(
                    FormalDiagnosticEvent(
                        "manual-check-${System.currentTimeMillis()}",
                        "手动诊断检查",
                        "本地数据库、资料解析和模型配置已读取",
                        "完成",
                        false
                    )
                )
            },
            onClearCache = {
                val cleared = context.cacheDir.clearChildren()
                refresh(
                    FormalDiagnosticEvent(
                        "cache-${System.currentTimeMillis()}",
                        "清理应用缓存",
                        if (cleared) "缓存目录已清理" else "缓存目录清理未完成",
                        if (cleared) "完成" else "警告",
                        !cleared
                    )
                )
            },
            onGenerateReport = {
                scope.launch {
                    state = coordinator.diagnostics(context, showWipeConfirmation = false)
                    events = events.filterNot { it.id.startsWith(GenerationDiagnosticEventPrefix) } +
                        coordinator.generationDiagnosticEvents()
                    report = FormalBatch6RuntimeStateFactory.diagnosticReport(state, events)
                    page = DiagnosticsPage.Report
                }
            },
            onRequestWipe = { state = state.copy(showWipeConfirmation = true) },
            onDismissWipe = { state = state.copy(showWipeConfirmation = false) },
            onExportBeforeWipe = {
                scope.launch {
                    val result = coordinator.fullBackup()
                    val json = result.json
                    if (result.isValid && !json.isNullOrBlank()) {
                        pendingDocument = RuntimeOutputDocument(
                            result.targetFileName,
                            json.toByteArray(Charsets.UTF_8),
                            "擦除前完整备份"
                        )
                        reportSaveLauncher.launch(result.targetFileName)
                    }
                }
            },
            onConfirmWipe = {
                scope.launch {
                    val result = hybridAppGraph.localDataWipeRepository.wipeLocalData(System.currentTimeMillis())
                    events = listOf(
                        FormalDiagnosticEvent(
                            "wipe-${result.completedAtEpochMillis}",
                            "擦除本地数据",
                            "已移除 ${result.deletedSecretRefCount} 个密钥引用并重建本地默认空间",
                            "完成",
                            false
                        )
                    ) + events
                    state = coordinator.diagnostics(context, showWipeConfirmation = false)
                }
            },
            modifier = modifier
        )

        DiagnosticsPage.Report -> report?.let { current ->
            FormalDiagnosticReportScreen(
                state = current,
                onBack = { page = DiagnosticsPage.Overview },
                onRegenerate = {
                    scope.launch {
                        state = coordinator.diagnostics(context, showWipeConfirmation = false)
                        events = events.filterNot { it.id.startsWith(GenerationDiagnosticEventPrefix) } +
                            coordinator.generationDiagnosticEvents()
                        report = FormalBatch6RuntimeStateFactory.diagnosticReport(state, events)
                    }
                },
                onCopySummary = {
                    context.copyPlainText("Reverse Tutor 诊断摘要", current.toPlainText())
                },
                onExportReport = {
                    val text = current.toPlainText()
                    val fileName = "reverse-tutor-diagnostics-${System.currentTimeMillis()}.txt"
                    pendingDocument = RuntimeOutputDocument(
                        fileName,
                        text.toByteArray(Charsets.UTF_8),
                        "诊断报告"
                    )
                    reportSaveLauncher.launch(fileName)
                },
                modifier = modifier
            )
        }
    }
}

@Composable
fun FormalTokenRoute(
    hybridAppGraph: HybridAppGraph,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val coordinator = remember(hybridAppGraph) { FormalBatch6RuntimeCoordinator(hybridAppGraph) }
    var page by remember { mutableStateOf(TokenPage.Overview) }
    var period by remember { mutableStateOf(FormalTokenPeriod.Week) }
    var query by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf(FormalBatch6RuntimeStateFactory.emptyTokenSnapshot(period)) }

    fun reload(selectedPeriod: FormalTokenPeriod = period, searchQuery: String = query) {
        scope.launch {
            snapshot = coordinator.tokenSnapshot(selectedPeriod, searchQuery)
        }
    }

    LaunchedEffect(Unit) { reload() }

    val onPeriodSelected: (FormalTokenPeriod) -> Unit = { selected ->
        period = selected
        reload(selected, query)
    }

    when (page) {
        TokenPage.Overview -> FormalTokenOverviewScreen(
            state = snapshot.overview,
            onBack = onBack,
            onPeriodSelected = onPeriodSelected,
            onOpenByModel = { page = TokenPage.ByModel },
            onOpenBySession = { page = TokenPage.BySession },
            modifier = modifier
        )

        TokenPage.ByModel -> FormalTokenByModelScreen(
            state = snapshot.byModel,
            onBack = { page = TokenPage.Overview },
            onPeriodSelected = onPeriodSelected,
            onOpenBySession = { page = TokenPage.BySession },
            modifier = modifier
        )

        TokenPage.BySession -> FormalTokenBySessionScreen(
            state = snapshot.bySession,
            onBack = { page = TokenPage.Overview },
            onPeriodSelected = onPeriodSelected,
            onSearchQueryChange = { value ->
                query = value
                reload(period, value)
            },
            onSessionClick = { id -> if (id.isNotBlank()) onOpenSession(id) },
            modifier = modifier
        )
    }
}

@Composable
fun FormalUpdateRoute(
    hybridAppGraph: HybridAppGraph,
    onBack: () -> Unit,
    onAutomaticUpdatesChange: (Boolean) -> Unit = {},
    onWifiOnlyChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember(hybridAppGraph) { FormalBatch6RuntimeCoordinator(hybridAppGraph) }
    var automaticUpdatesEnabled by remember { mutableStateOf(false) }
    var wifiOnlyEnabled by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<ReleaseMetadata?>(null) }
    var lastCheckedLabel by remember {
        mutableStateOf(if (hybridAppGraph.online == null) "未连接服务" else "尚未检查")
    }
    var checkedSuccessfully by remember { mutableStateOf(false) }
    val appInfo = remember(context) { context.runtimeAppInfo() }

    fun state(): FormalUpdateUiState = FormalBatch6RuntimeStateFactory.update(
        release = release,
        checkedSuccessfully = checkedSuccessfully,
        lastCheckedLabel = lastCheckedLabel,
        automaticUpdatesEnabled = automaticUpdatesEnabled,
        wifiOnlyEnabled = wifiOnlyEnabled,
        appInfo = appInfo
    )

    FormalUpdateScreen(
        state = state(),
        onBack = onBack,
        onAutomaticUpdatesChange = { enabled ->
            automaticUpdatesEnabled = enabled
            onAutomaticUpdatesChange(enabled)
        },
        onWifiOnlyChange = { enabled ->
            wifiOnlyEnabled = enabled
            onWifiOnlyChange(enabled)
        },
        onOpenChannel = {},
        onCheckUpdate = {
            scope.launch {
                val result = coordinator.latestRelease()
                release = result
                checkedSuccessfully = result != null
                lastCheckedLabel = when {
                    hybridAppGraph.online == null -> "未连接服务"
                    result == null -> "检查失败"
                    else -> formatClock(System.currentTimeMillis())
                }
            }
        },
        onOpenCurrentReleaseNotes = {},
        onDismissAvailableUpdate = { release = null },
        onDownloadUpdate = {
            val url = release?.downloadUrl
            if (!url.isNullOrBlank()) context.openExternalUrl(url)
        },
        modifier = modifier
    )
}

@Composable
fun FormalSyncConflictRoute(
    hybridAppGraph: HybridAppGraph,
    resolver: FormalSyncConflictResolver,
    onBack: () -> Unit,
    onNoConflicts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val coordinator = remember(hybridAppGraph) { FormalBatch6RuntimeCoordinator(hybridAppGraph) }
    var conflicts by remember { mutableStateOf(emptyList<SyncConflict>()) }
    var activeConflict by remember { mutableStateOf<SyncConflict?>(null) }
    var selectedSource by remember { mutableStateOf(FormalSyncSource.Device) }
    var loaded by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            conflicts = coordinator.pendingSyncConflicts()
            loaded = true
            if (conflicts.isEmpty()) onNoConflicts()
        }
    }

    LaunchedEffect(Unit) { reload() }

    val current = activeConflict
    if (current != null) {
        FormalSyncConflictChoiceScreen(
            state = FormalBatch6RuntimeStateFactory.syncChoice(current, conflicts, selectedSource),
            onBack = { activeConflict = null },
            onSourceSelected = { selectedSource = it },
            onReturnToList = { activeConflict = null },
            onConfirmAndContinue = {
                scope.launch {
                    if (resolver.resolve(current, selectedSource)) {
                        val remaining = coordinator.pendingSyncConflicts()
                        conflicts = remaining
                        val next = remaining.firstOrNull()
                        activeConflict = next
                        selectedSource = FormalSyncSource.Device
                        if (next == null) onNoConflicts()
                    }
                }
            },
            modifier = modifier
        )
    } else if (loaded && conflicts.isNotEmpty()) {
        FormalSyncConflictOverviewScreen(
            state = FormalBatch6RuntimeStateFactory.syncOverview(conflicts, hybridAppGraph.online != null),
            onBack = onBack,
            onConflictClick = { id ->
                activeConflict = conflicts.firstOrNull { it.id == id }
                selectedSource = FormalSyncSource.Device
            },
            onPostpone = onBack,
            onResolveInOrder = {
                activeConflict = conflicts.firstOrNull()
                selectedSource = FormalSyncSource.Device
            },
            modifier = modifier
        )
    }
}

private class FormalBatch6RuntimeCoordinator(
    private val graph: HybridAppGraph
) {
    suspend fun importExportOverview(recentRecords: List<FormalTransferRecord>): FormalImportExportUiState {
        val sessions = graph.sessionRepository.listSessions().filterNot { it.archived }
        val messageCount = sessions.sumOf { graph.messageRepository.listMessages(it.id).size }
        val materials = graph.sourceRepository.listSourcesWithChunks()
        val nodes = graph.graphRepository.snapshot().nodes
        return FormalImportExportUiState(
            localSessionCount = sessions.size,
            localMessageCount = messageCount,
            localMaterialCount = materials.size,
            localKnowledgeNodeCount = nodes.size,
            recentRecords = recentRecords
        )
    }

    suspend fun dryRunImport(document: RuntimeImportDocument, mode: FormalImportMode): NativeImportResult =
        graph.nativeImportRepository.dryRun(
            json = document.json,
            sourceFileName = document.fileName,
            nowEpochMillis = System.currentTimeMillis(),
            mode = mode.toNativeMode()
        )

    suspend fun import(document: RuntimeImportDocument, mode: FormalImportMode): NativeImportResult =
        graph.nativeImportRepository.importJson(
            json = document.json,
            sourceFileName = document.fileName,
            nowEpochMillis = System.currentTimeMillis(),
            mode = mode.toNativeMode(),
            overwriteConfirmed = mode == FormalImportMode.Overwrite
        )

    suspend fun fullBackup(): NativeExportResult =
        graph.nativeExportRepository.fullBackup(createdAt = isoUtc(System.currentTimeMillis()))

    suspend fun graphSnapshot(): NativeExportResult =
        graph.nativeExportRepository.graphSnapshot(createdAt = isoUtc(System.currentTimeMillis()))

    suspend fun currentSession(sessionId: String): NativeExportResult =
        graph.nativeExportRepository.currentSession(
            sessionId = sessionId,
            createdAt = isoUtc(System.currentTimeMillis())
        )

    suspend fun sessionExportSelection(
        selectedIds: Set<String>,
        query: String
    ): FormalSessionExportUiState {
        val sessions = graph.sessionRepository.listSessions().filterNot { it.archived }
        val allRows = sessions.mapIndexed { index, session ->
            val messages = graph.messageRepository.listMessages(session.id)
            SessionExportRuntimeRow(session, messages.size, messages.sumOf { it.text.toByteArray().size.toLong() }, index)
        }
        return FormalBatch6RuntimeStateFactory.sessionSelection(allRows, selectedIds, query)
    }

    suspend fun diagnostics(context: Context, showWipeConfirmation: Boolean): FormalDiagnosticsUiState {
        val sessionsResult = runCatching { graph.sessionRepository.listSessions().filterNot { it.archived } }
        val sessions = sessionsResult.getOrDefault(emptyList())
        val messageCount = sessions.sumOf { session ->
            runCatching { graph.messageRepository.listMessages(session.id).size }.getOrDefault(0)
        }
        val sources = runCatching { graph.sourceRepository.listSourcesWithChunks() }.getOrDefault(emptyList())
        val graphSnapshot = runCatching { graph.graphRepository.snapshot() }.getOrNull()
        val profiles = runCatching { graph.llmProfileRepository.listProfiles() }.getOrDefault(emptyList())
        val activeProfile = profiles.firstOrNull { it.enabled }
        val databaseBytes = context.databaseList()
            .sumOf { name -> context.getDatabasePath(name).length().coerceAtLeast(0L) }
        val cacheBytes = context.cacheDir.sizeRecursively()
        return FormalBatch6RuntimeStateFactory.diagnostics(
            appInfo = context.runtimeAppInfo(),
            sessionsReadable = sessionsResult.isSuccess,
            sessionCount = sessions.size,
            messageCount = messageCount,
            materialCount = sources.size,
            graphNodeCount = graphSnapshot?.nodes?.size ?: 0,
            sourceStatuses = sources.map { it.source.parserStatus },
            activeModelLabel = activeProfile?.let { "${it.name} · ${it.model}" },
            databaseBytes = databaseBytes,
            cacheBytes = cacheBytes,
            showWipeConfirmation = showWipeConfirmation
        )
    }

    suspend fun generationDiagnosticEvents(): List<FormalDiagnosticEvent> = runCatching {
        graph.memoryRepository.snapshot().errors
            .asSequence()
            .filter { it.origin == ErrorLogOrigin.Generation }
            .map { error ->
                val record = GenerationDiagnosticPolicy.forCode(error.code)
                FormalDiagnosticEvent(
                    id = "$GenerationDiagnosticEventPrefix${error.id}",
                    title = record.title,
                    subtitle = "${formatDateTime(error.createdAtEpochMillis)} · ${record.detail}",
                    statusLabel = "需关注",
                    warning = true
                )
            }
            .toList()
    }.getOrDefault(emptyList())

    suspend fun tokenSnapshot(period: FormalTokenPeriod, query: String): FormalTokenRuntimeSnapshot {
        val sessions = graph.sessionRepository.listSessions().filterNot { it.archived }
        val usage = graph.learningRepository.listTokenUsage(DefaultSpaceId)
        val turnToSession = buildMap {
            sessions.forEach { session ->
                graph.conversationRunRepository.listBySession(session.id).forEach { run ->
                    put(run.turnId, session.id)
                }
            }
        }
        val modelNames = usage.mapNotNull { it.modelBindingId }.distinct().associateWith { id ->
            graph.modelConnectionRepository.findBinding(id)?.displayName ?: id
        }
        return FormalBatch6RuntimeStateFactory.tokenSnapshot(
            allUsage = usage,
            sessions = sessions,
            turnToSession = turnToSession,
            modelNames = modelNames,
            period = period,
            query = query
        )
    }

    suspend fun latestRelease(): ReleaseMetadata? = graph.online?.updateRepository?.latestRelease()

    suspend fun pendingSyncConflicts(): List<SyncConflict> =
        graph.syncRepository.listPendingConflicts(DefaultSpaceId)
}

internal object FormalBatch6RuntimeStateFactory {
    private val palette = listOf(
        Color(0xFF4F8CCB),
        Color(0xFF43A17D),
        Color(0xFFB7802E),
        Color(0xFF7659B8),
        Color(0xFFC25D69)
    )

    fun emptyImportExport(): FormalImportExportUiState =
        FormalImportExportUiState(0, 0, 0, 0, emptyList())

    fun emptySessionSelection(): FormalSessionExportUiState =
        FormalSessionExportUiState("", 0, 0, "0 B", emptyList(), false)

    fun importPreview(
        document: RuntimeImportDocument,
        result: NativeImportResult,
        mode: FormalImportMode
    ): FormalImportPreviewUiState {
        val issues = buildList {
            result.errors.forEachIndexed { index, message ->
                add(
                    FormalImportIssue(
                        id = "error-$index",
                        title = "校验阻止导入",
                        subtitle = message,
                        severity = if (message.isSecretMessage()) {
                            FormalImportIssueSeverity.Secret
                        } else {
                            FormalImportIssueSeverity.Blocking
                        }
                    )
                )
            }
            result.warnings.forEachIndexed { index, message ->
                add(
                    FormalImportIssue(
                        id = "warning-$index",
                        title = if (message.isSecretMessage()) "密钥材料不会导入" else "兼容性提醒",
                        subtitle = message,
                        severity = if (message.isSecretMessage()) {
                            FormalImportIssueSeverity.Secret
                        } else {
                            FormalImportIssueSeverity.Warning
                        }
                    )
                )
            }
        }
        val totalRecords = result.insertedCounts.values.sum()
        return FormalImportPreviewUiState(
            fileName = document.fileName,
            fileMeta = "${formatBytes(document.sizeBytes)} · ${result.sourceSchema}",
            isValidated = result.errors.isEmpty(),
            sessionCount = result.countFor("session"),
            messageCount = result.countFor("message"),
            materialCount = result.countFor("source", "material"),
            planCount = result.countFor("plan", "task"),
            issues = issues,
            selectedMode = mode,
            targetSpaceLabel = when (mode) {
                FormalImportMode.Append -> "当前本地空间"
                FormalImportMode.Overwrite -> "当前本地空间（将替换）"
                FormalImportMode.NewSpace -> "新导入空间"
            },
            estimatedSizeLabel = formatBytes(document.sizeBytes),
            summaryLabel = if (result.errors.isEmpty()) {
                "预计写入 $totalRecords 条记录"
            } else {
                "发现 ${result.errors.size} 个阻止项"
            },
            canImport = result.canWrite
        )
    }

    fun importRecord(result: NativeImportResult): FormalTransferRecord =
        FormalTransferRecord(
            id = result.batchId,
            title = "导入 ${result.sourceFileName}",
            subtitle = "${result.insertedCounts.values.sum()} 条写入 · ${result.warnings.size} 条提醒",
            status = if (result.status == NativeImportStatus.Completed) {
                FormalTransferStatus.Completed
            } else {
                FormalTransferStatus.Warning
            }
        )

    fun sessionSelection(
        rows: List<SessionExportRuntimeRow>,
        selectedIds: Set<String>,
        query: String
    ): FormalSessionExportUiState {
        val filtered = rows.filter { it.session.title.contains(query, ignoreCase = true) }
        val selectedBytes = rows.filter { it.session.id in selectedIds }.sumOf { it.estimatedBytes }
        return FormalSessionExportUiState(
            searchQuery = query,
            totalCount = rows.size,
            selectedCount = selectedIds.size,
            selectedSizeLabel = formatBytes(selectedBytes),
            sessions = filtered.map { row ->
                FormalExportSessionItem(
                    id = row.session.id,
                    title = row.session.title,
                    subtitle = "${row.messageCount} 条消息 · 更新于 ${formatShortDate(row.session.updatedAtEpochMillis)}",
                    selected = row.session.id in selectedIds,
                    color = palette[row.colorIndex % palette.size]
                )
            },
            canExport = selectedIds.isNotEmpty()
        )
    }

    fun sessionExportDocument(results: List<NativeExportResult>): RuntimeOutputDocument? {
        if (results.isEmpty() || results.any { !it.isValid || it.json.isNullOrBlank() }) return null
        if (results.size == 1) {
            val result = results.single()
            return RuntimeOutputDocument(
                result.targetFileName,
                result.json!!.toByteArray(Charsets.UTF_8),
                "会话导出"
            )
        }
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                results.forEachIndexed { index, result ->
                    val fileName = result.targetFileName.sanitizedZipEntry(index)
                    zip.putNextEntry(ZipEntry(fileName))
                    zip.write(result.json!!.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
        return RuntimeOutputDocument(
            fileName = "reverse-tutor-sessions-${System.currentTimeMillis()}.zip",
            bytes = bytes,
            recordTitle = "${results.size} 个会话导出"
        )
    }

    fun emptyDiagnostics(appInfo: RuntimeAppInfo): FormalDiagnosticsUiState = diagnostics(
        appInfo = appInfo,
        sessionsReadable = false,
        sessionCount = 0,
        messageCount = 0,
        materialCount = 0,
        graphNodeCount = 0,
        sourceStatuses = emptyList(),
        activeModelLabel = null,
        databaseBytes = 0,
        cacheBytes = 0,
        showWipeConfirmation = false
    )

    fun diagnostics(
        appInfo: RuntimeAppInfo,
        sessionsReadable: Boolean,
        sessionCount: Int,
        messageCount: Int,
        materialCount: Int,
        graphNodeCount: Int,
        sourceStatuses: List<SourceParserStatus>,
        activeModelLabel: String?,
        databaseBytes: Long,
        cacheBytes: Long,
        showWipeConfirmation: Boolean
    ): FormalDiagnosticsUiState {
        val parserProblems = sourceStatuses.count { it == SourceParserStatus.Failed || it == SourceParserStatus.Unsupported }
        val totalStorage = databaseBytes + cacheBytes
        val databaseFraction = if (totalStorage == 0L) 0f else databaseBytes.toFloat() / totalStorage
        val cacheFraction = if (totalStorage == 0L) 0f else cacheBytes.toFloat() / totalStorage
        return FormalDiagnosticsUiState(
            appVersionLabel = "${appInfo.versionName} (${appInfo.versionCode})",
            deviceLabel = deviceLabel(),
            currentSpaceLabel = "本地默认空间",
            localUsageLabel = "$sessionCount 个会话 · $messageCount 条消息",
            modelLabel = activeModelLabel ?: "未配置默认模型",
            modelStatusLabel = if (activeModelLabel == null) "待配置" else "已连接本地配置",
            systemRows = listOf(
                FormalDiagnosticStatusRow(
                    "database",
                    "本地数据库",
                    if (sessionsReadable) "Room 仓库读取正常" else "本地仓库读取失败",
                    if (sessionsReadable) "正常" else "检查",
                    if (sessionsReadable) FormalDiagnosticTone.Success else FormalDiagnosticTone.Warning,
                    FormalDiagnosticIcon.Database
                ),
                FormalDiagnosticStatusRow(
                    "parser",
                    "资料解析",
                    "$materialCount 份资料 · $parserProblems 个失败或不支持项",
                    if (parserProblems == 0) "正常" else "提醒",
                    if (parserProblems == 0) FormalDiagnosticTone.Info else FormalDiagnosticTone.Warning,
                    FormalDiagnosticIcon.Parser
                ),
                FormalDiagnosticStatusRow(
                    "model",
                    "默认模型",
                    activeModelLabel ?: "模型密钥只保存在本机",
                    if (activeModelLabel == null) "未配置" else "已配置",
                    if (activeModelLabel == null) FormalDiagnosticTone.Warning else FormalDiagnosticTone.Success,
                    FormalDiagnosticIcon.Model
                )
            ),
            storageUsedLabel = formatBytes(totalStorage),
            storageSegments = listOf(
                FormalStorageSegment("数据库", formatBytes(databaseBytes), databaseFraction, Color(0xFF4F8CCB)),
                FormalStorageSegment("缓存", formatBytes(cacheBytes), cacheFraction, Color(0xFF43A17D))
            ),
            showWipeConfirmation = showWipeConfirmation,
            wipeSummary = "$sessionCount 个会话 · $materialCount 份资料 · $graphNodeCount 个知识节点"
        )
    }

    fun diagnosticReport(
        state: FormalDiagnosticsUiState,
        events: List<FormalDiagnosticEvent>
    ): FormalDiagnosticReportUiState {
        val now = System.currentTimeMillis()
        return FormalDiagnosticReportUiState(
            reportId = "RT-${now.toString().takeLast(8)}",
            createdAtLabel = formatDateTime(now),
            appVersionLabel = state.appVersionLabel,
            deviceLabel = state.deviceLabel,
            spaceLabel = state.currentSpaceLabel,
            databaseLabel = state.systemRows.firstOrNull { it.id == "database" }?.statusLabel ?: "未知",
            localDataLabel = state.localUsageLabel,
            parserLabel = state.systemRows.firstOrNull { it.id == "parser" }?.subtitle ?: "未检查",
            recentEvents = events.take(8)
        )
    }

    fun emptyTokenSnapshot(period: FormalTokenPeriod): FormalTokenRuntimeSnapshot =
        tokenSnapshot(emptyList(), emptyList(), emptyMap(), emptyMap(), period, "")

    fun tokenSnapshot(
        allUsage: List<TokenUsageRecord>,
        sessions: List<TutorSession>,
        turnToSession: Map<String, String>,
        modelNames: Map<String, String>,
        period: FormalTokenPeriod,
        query: String,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): FormalTokenRuntimeSnapshot {
        val range = periodRange(period, nowEpochMillis)
        val usage = allUsage.filter { it.createdAtEpochMillis in range }
        val summary = usageSummary(usage, allUsage, period, range)
        val trend = trendPoints(usage, range, nowEpochMillis)
        val byModel = usage.groupBy { it.modelBindingId ?: "unbound" }
            .entries
            .sortedByDescending { (_, records) -> records.sumOf { it.totalTokens } }
            .mapIndexed { index, (id, records) ->
                val total = records.sumOf { it.totalTokens }
                val denominator = usage.sumOf { it.totalTokens }.coerceAtLeast(1L)
                FormalTokenModelItem(
                    id = id,
                    name = modelNames[id] ?: if (id == "unbound") "未绑定模型" else id,
                    tokenLabel = formatTokens(total),
                    percentageLabel = formatPercent(total, denominator),
                    breakdownLabel = "输入 ${formatTokens(records.sumOf { it.inputTokens })} · 输出 ${formatTokens(records.sumOf { it.outputTokens })}",
                    fraction = total.toFloat() / denominator,
                    color = palette[index % palette.size]
                )
            }
        val sessionsById = sessions.associateBy { it.id }
        val bySession = usage.groupBy { turnToSession[it.turnId] }
            .entries
            .sortedByDescending { (_, records) -> records.sumOf { it.totalTokens } }
            .mapIndexed { index, (sessionId, records) ->
                val total = records.sumOf { it.totalTokens }
                val denominator = usage.sumOf { it.totalTokens }.coerceAtLeast(1L)
                val title = sessionId?.let { sessionsById[it]?.title } ?: "未关联会话"
                val modelIds = records.mapNotNull { it.modelBindingId }.distinct()
                FormalTokenSessionItem(
                    id = sessionId.orEmpty(),
                    title = title,
                    modelName = modelIds.joinToString(" / ") { modelNames[it] ?: it }.ifBlank { "未绑定模型" },
                    tokenLabel = formatTokens(total),
                    percentageLabel = formatPercent(total, denominator),
                    breakdownLabel = "${records.size} 次生成 · 输入 ${formatTokens(records.sumOf { it.inputTokens })} · 输出 ${formatTokens(records.sumOf { it.outputTokens })}",
                    fraction = total.toFloat() / denominator,
                    color = palette[index % palette.size]
                )
            }
            .filter { it.title.contains(query, ignoreCase = true) || it.modelName.contains(query, ignoreCase = true) }
        val overview = FormalTokenOverviewUiState(
            selectedPeriod = period,
            summary = summary,
            peakLabel = usage.maxByOrNull { it.totalTokens }?.let { formatTokens(it.totalTokens) } ?: "0",
            averageLabel = if (usage.isEmpty()) "0" else formatTokens(usage.sumOf { it.totalTokens } / usage.size),
            trend = trend,
            modelSummary = "${byModel.size} 个模型 · ${byModel.firstOrNull()?.name ?: "暂无记录"}",
            sessionSummary = "${bySession.count { it.id.isNotBlank() }} 个会话"
        )
        return FormalTokenRuntimeSnapshot(
            overview = overview,
            byModel = FormalTokenByModelUiState(
                selectedPeriod = period,
                summary = summary,
                leadingModelLabel = byModel.firstOrNull()?.name ?: "暂无模型用量",
                items = byModel
            ),
            bySession = FormalTokenBySessionUiState(
                selectedPeriod = period,
                summary = summary,
                activeSessionCountLabel = "${bySession.count { it.id.isNotBlank() }} 个活跃会话",
                searchQuery = query,
                items = bySession
            )
        )
    }

    fun update(
        release: ReleaseMetadata?,
        checkedSuccessfully: Boolean,
        lastCheckedLabel: String,
        automaticUpdatesEnabled: Boolean,
        wifiOnlyEnabled: Boolean,
        appInfo: RuntimeAppInfo
    ): FormalUpdateUiState {
        val available = release?.takeIf { it.versionCode > appInfo.versionCode }?.let {
            FormalAvailableUpdate(
                versionLabel = "v${it.versionName}",
                sizeLabel = "大小未知",
                changes = listOf("服务端未提供版本说明")
            )
        }
        return FormalUpdateUiState(
            currentVersionLabel = "v${appInfo.versionName}",
            channelLabel = "稳定渠道",
            lastCheckedLabel = lastCheckedLabel,
            isLatest = checkedSuccessfully && release != null && release.versionCode <= appInfo.versionCode,
            automaticUpdatesEnabled = automaticUpdatesEnabled,
            wifiOnlyEnabled = wifiOnlyEnabled,
            currentReleaseSummary = "当前安装版本 ${appInfo.versionName}",
            archivedVersionLabel = "无",
            availableUpdate = available
        )
    }

    fun syncOverview(conflicts: List<SyncConflict>, online: Boolean): FormalSyncConflictUiState {
        val detectedAt = conflicts.minOfOrNull { it.createdAtEpochMillis } ?: System.currentTimeMillis()
        return FormalSyncConflictUiState(
            conflictCount = conflicts.size,
            detectedAtLabel = formatDateTime(detectedAt),
            conflictItems = conflicts.map { conflict ->
                FormalSyncConflictItem(
                    id = conflict.id,
                    title = conflict.entityId,
                    categoryLabel = conflict.entityType.toEntityTypeLabel(),
                    differenceSummary = "本地版本 ${conflict.localRevision} · 云端版本 ${conflict.remoteRevision}",
                    versionSummary = "两端均有修改，等待选择保留内容",
                    icon = if (conflict.entityType.contains("plan", true)) {
                        FormalSyncConflictIcon.Calendar
                    } else {
                        FormalSyncConflictIcon.Tree
                    }
                )
            },
            autoMergedCount = 0,
            autoMergedMaterialCount = 0,
            localDeviceLabel = androidModelLabel(),
            cloudSpaceLabel = "可选同步空间",
            cloudStatusLabel = if (online) "在线服务已连接" else "同步服务未连接"
        )
    }

    fun syncChoice(
        conflict: SyncConflict,
        allConflicts: List<SyncConflict>,
        selectedSource: FormalSyncSource
    ): FormalSyncChoiceUiState {
        val index = allConflicts.indexOfFirst { it.id == conflict.id }.coerceAtLeast(0)
        return FormalSyncChoiceUiState(
            title = conflict.entityId,
            stepLabel = "${index + 1}/${allConflicts.size.coerceAtLeast(1)}",
            progressFraction = (index + 1).toFloat() / allConflicts.size.coerceAtLeast(1),
            differenceCount = 1,
            differenceSummary = conflict.entityType.toEntityTypeLabel(),
            options = listOf(
                FormalSyncChoiceOption(
                    FormalSyncSource.Device,
                    "保留此设备版本",
                    "版本 ${conflict.localRevision}",
                    listOf(
                        FormalSyncChoiceValue("来源", androidModelLabel()),
                        FormalSyncChoiceValue("内容", if (conflict.localPayload == null) "无附加内容" else "内容已脱敏，不在选择页展开")
                    )
                ),
                FormalSyncChoiceOption(
                    FormalSyncSource.Cloud,
                    "保留云端版本",
                    "版本 ${conflict.remoteRevision}",
                    listOf(
                        FormalSyncChoiceValue("来源", "在线同步服务"),
                        FormalSyncChoiceValue("内容", if (conflict.remotePayload == null) "无附加内容" else "内容已脱敏，不在选择页展开")
                    )
                )
            ),
            selectedSource = selectedSource
        )
    }

    private fun usageSummary(
        usage: List<TokenUsageRecord>,
        allUsage: List<TokenUsageRecord>,
        period: FormalTokenPeriod,
        range: LongRange
    ): FormalTokenSummary {
        val total = usage.sumOf { it.totalTokens }
        val comparison = if (period == FormalTokenPeriod.All || range.first == Long.MIN_VALUE) {
            "累计 ${usage.size} 次生成"
        } else {
            val duration = (range.last - range.first + 1L).coerceAtLeast(1L)
            val previousStart = range.first - duration
            val previousEnd = range.first - 1L
            val previous = allUsage.filter { it.createdAtEpochMillis in previousStart..previousEnd }.sumOf { it.totalTokens }
            when {
                previous == 0L && total == 0L -> "与上一周期持平"
                previous == 0L -> "上一周期无记录"
                else -> {
                    val delta = ((total - previous) * 100.0 / previous).toInt()
                    if (delta >= 0) "较上一周期 +$delta%" else "较上一周期 $delta%"
                }
            }
        }
        return FormalTokenSummary(
            totalLabel = formatTokens(total),
            comparisonLabel = comparison,
            estimateLabel = when {
                usage.isEmpty() -> "无记录"
                usage.all { it.estimated } -> "估算"
                usage.none { it.estimated } -> "实测"
                else -> "含估算"
            },
            inputLabel = formatTokens(usage.sumOf { it.inputTokens }),
            outputLabel = formatTokens(usage.sumOf { it.outputTokens }),
            cacheLabel = formatTokens(usage.sumOf { it.cachedTokens }),
            reasoningLabel = formatTokens(usage.sumOf { it.reasoningTokens })
        )
    }

    private fun trendPoints(
        usage: List<TokenUsageRecord>,
        range: LongRange,
        nowEpochMillis: Long
    ): List<FormalTokenTrendPoint> {
        val effectiveStart = if (range.first == Long.MIN_VALUE) {
            usage.minOfOrNull { it.createdAtEpochMillis } ?: (nowEpochMillis - 6L * dayMillis)
        } else {
            range.first
        }
        val effectiveEnd = if (range.last == Long.MAX_VALUE) nowEpochMillis else range.last
        val bucketSize = ((effectiveEnd - effectiveStart + 1L) / 7L).coerceAtLeast(1L)
        val values = (0 until 7).map { index ->
            val start = effectiveStart + bucketSize * index
            val end = if (index == 6) effectiveEnd else start + bucketSize - 1L
            start to usage.filter { it.createdAtEpochMillis in start..end }.sumOf { it.totalTokens }
        }
        val max = values.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
        return values.map { (start, value) ->
            FormalTokenTrendPoint(
                label = SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(start)),
                valueLabel = formatTokens(value),
                fraction = value.toFloat() / max
            )
        }
    }
}

internal data class FormalTokenRuntimeSnapshot(
    val overview: FormalTokenOverviewUiState,
    val byModel: FormalTokenByModelUiState,
    val bySession: FormalTokenBySessionUiState
)

internal data class RuntimeImportDocument(
    val fileName: String,
    val json: String,
    val sizeBytes: Long
)

internal data class SessionExportRuntimeRow(
    val session: TutorSession,
    val messageCount: Int,
    val estimatedBytes: Long,
    val colorIndex: Int
)

internal data class RuntimeOutputDocument(
    val fileName: String,
    val bytes: ByteArray,
    val recordTitle: String
)

internal data class RuntimeAppInfo(
    val versionName: String,
    val versionCode: Long
)

private fun FormalImportMode.toNativeMode(): NativeImportMode = when (this) {
    FormalImportMode.Append -> NativeImportMode.Append
    FormalImportMode.Overwrite -> NativeImportMode.Overwrite
    FormalImportMode.NewSpace -> NativeImportMode.NewSpace
}

private fun NativeImportResult.countFor(vararg fragments: String): Int =
    insertedCounts.entries.filter { (key, _) -> fragments.any { key.contains(it, ignoreCase = true) } }.sumOf { it.value }

private fun String.isSecretMessage(): Boolean =
    contains("secret", true) || contains("api key", true) || contains("token", true) || contains("password", true) || contains("密钥")

private fun Context.displayName(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        cursor.getString(0)
    }

@Suppress("DEPRECATION")
private fun Context.runtimeAppInfo(): RuntimeAppInfo {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        packageInfo.versionCode.toLong()
    }
    return RuntimeAppInfo(
        versionName = packageInfo.versionName ?: "unknown",
        versionCode = versionCode
    )
}

private fun Context.copyPlainText(label: String, value: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun Context.openExternalUrl(url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    if (uri.scheme !in setOf("https", "http")) return
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun File.sizeRecursively(): Long =
    when {
        !exists() -> 0L
        isFile -> length()
        else -> listFiles()?.sumOf { it.sizeRecursively() } ?: 0L
    }

private fun File.clearChildren(): Boolean =
    listFiles()?.map { it.deleteRecursively() }?.all { it } ?: true

internal fun FormalDiagnosticReportUiState.toPlainText(): String = buildString {
    appendLine("Reverse Tutor 诊断报告")
    appendLine("报告编号：$reportId")
    appendLine("生成时间：$createdAtLabel")
    appendLine("应用版本：$appVersionLabel")
    appendLine("设备系统：$deviceLabel")
    appendLine("数据空间：$spaceLabel")
    appendLine("数据库：$databaseLabel")
    appendLine("本地数据：$localDataLabel")
    appendLine("资料解析：$parserLabel")
    recentEvents.forEach { event ->
        appendLine("事件：${event.title} · ${event.statusLabel} · ${event.subtitle}")
    }
    append("不包含 API Key、完整请求或用户隐私正文。")
}

private fun String.sanitizedZipEntry(index: Int): String {
    val safe = substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "-")
    return safe.ifBlank { "session-$index.json" }.let { if (it.endsWith(".json")) it else "$it.json" }
}

private fun String.toEntityTypeLabel(): String = when {
    contains("world", true) || contains("tree", true) || contains("graph", true) -> "世界树设置"
    contains("plan", true) || contains("task", true) -> "学习计划"
    contains("session", true) -> "会话设置"
    else -> "同步记录"
}

private fun periodRange(period: FormalTokenPeriod, nowEpochMillis: Long): LongRange {
    if (period == FormalTokenPeriod.All) return Long.MIN_VALUE..Long.MAX_VALUE
    val calendar = Calendar.getInstance().apply { timeInMillis = nowEpochMillis }
    when (period) {
        FormalTokenPeriod.Today -> Unit
        FormalTokenPeriod.Week -> calendar.add(Calendar.DAY_OF_YEAR, -6)
        FormalTokenPeriod.Month -> calendar.add(Calendar.DAY_OF_YEAR, -29)
        FormalTokenPeriod.All -> Unit
    }
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis..nowEpochMillis
}

private fun androidModelLabel(): String =
    Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android 设备"

private fun deviceLabel(): String {
    val manufacturer = Build.MANUFACTURER?.takeIf { it.isNotBlank() }.orEmpty()
    val release = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "未知版本"
    return listOf(manufacturer, androidModelLabel()).filter { it.isNotBlank() }.joinToString(" ") + " · Android $release"
}

private fun formatBytes(value: Long): String = when {
    value < 1_024L -> "$value B"
    value < 1_024L * 1_024L -> String.format(Locale.CHINA, "%.1f KB", value / 1_024.0)
    value < 1_024L * 1_024L * 1_024L -> String.format(Locale.CHINA, "%.1f MB", value / (1_024.0 * 1_024.0))
    else -> String.format(Locale.CHINA, "%.1f GB", value / (1_024.0 * 1_024.0 * 1_024.0))
}

private fun formatTokens(value: Long): String = when {
    value < 1_000L -> value.toString()
    value < 1_000_000L -> String.format(Locale.CHINA, "%.1fK", value / 1_000.0)
    else -> String.format(Locale.CHINA, "%.2fM", value / 1_000_000.0)
}

private fun formatPercent(value: Long, denominator: Long): String =
    "${(value * 100.0 / denominator.coerceAtLeast(1L)).toInt()}%"

private fun formatClock(value: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(value))

private fun formatShortDate(value: Long): String =
    SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(value))

private fun formatDateTime(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(value))

private fun isoUtc(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(value))

private const val dayMillis = 24L * 60L * 60L * 1_000L
