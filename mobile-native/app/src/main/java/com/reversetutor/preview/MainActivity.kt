package com.reversetutor.preview

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.reversetutor.core.data.preferences.AppPreferences
import com.reversetutor.preview.background.AndroidBackgroundGenerationNotifier
import com.reversetutor.preview.background.BackgroundGenerationStartupRecovery
import com.reversetutor.preview.background.BackgroundGenerationWorker
import com.reversetutor.preview.shell.AppShell
import com.reversetutor.preview.theme.ReverseTutorTheme
import com.reversetutor.preview.wiring.DebugLlmBootstrapConfig
import com.reversetutor.preview.wiring.DebugLlmProfileBootstrapper
import com.reversetutor.preview.wiring.DebugGraphScenarioSeeder
import com.reversetutor.preview.wiring.HybridAppGraph
import com.reversetutor.preview.wiring.HybridOnlineConfiguration
import com.reversetutor.preview.wiring.RepositoryDebugLlmProfileStore
import com.reversetutor.preview.wiring.runtimeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var receivedImportPayload by mutableStateOf<ReceivedImportPayload?>(null)
    private var pendingOpenSessionId by mutableStateOf<String?>(null)

    private val debugLlmConfig by lazy {
        DebugLlmBootstrapConfig.from(
            apiKey = BuildConfig.DEBUG_LLM_API_KEY,
            baseUrl = BuildConfig.DEBUG_LLM_BASE_URL,
            defaultModel = BuildConfig.DEBUG_LLM_DEFAULT_MODEL,
            fallbackModels = BuildConfig.DEBUG_LLM_FALLBACK_MODELS
        )
    }

    private val appGraph by lazy {
        HybridAppGraph.create(
            context = this,
            onlineConfiguration = HybridOnlineConfiguration.fromBaseUrl(
                BuildConfig.ONLINE_API_BASE_URL
            ),
            llmRuntimeMode = debugLlmConfig.runtimeMode()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        receivedImportPayload = readImportPayload(intent)
        pendingOpenSessionId = readOpenSessionId(intent)
        if (BuildConfig.DEBUG) {
            lifecycleScope.launch {
                DebugGraphScenarioSeeder(appGraph).ensureSeeded(System.currentTimeMillis())
            }
        }
        lifecycleScope.launch {
            DebugLlmProfileBootstrapper(
                store = RepositoryDebugLlmProfileStore(appGraph.llmProfileRepository)
            ).ensureProfiles(debugLlmConfig)
        }
        lifecycleScope.launch {
            val backgroundGenerationRepository = appGraph.backgroundGenerationRepository
            BackgroundGenerationStartupRecovery(
                recoverJobIds = { nowEpochMillis ->
                    backgroundGenerationRepository
                        .recoverInterruptedGenerationJobs(nowEpochMillis)
                        .map { it.id }
                },
                enqueue = { jobId -> BackgroundGenerationWorker.enqueue(this@MainActivity, jobId) }
            ).recoverAndSchedule(System.currentTimeMillis())
        }
        setContent {
            val appPreferences by appGraph.appPreferencesRepository.preferences.collectAsState(
                initial = AppPreferences.defaults
            )
            val receivedImport = receivedImportPayload

            ReverseTutorTheme {
                AppShell(
                    hybridAppGraph = appGraph,
                    appPreferences = appPreferences,
                    sessionRepository = appGraph.sessionRepository,
                    messageRepository = appGraph.messageRepository,
                    llmProfileRepository = appGraph.llmProfileRepository,
                    chatGenerationRepository = appGraph.chatGenerationRepository,
                    backgroundGenerationRepository = appGraph.backgroundGenerationRepository,
                    sourceRepository = appGraph.sourceRepository,
                    memoryRepository = appGraph.memoryRepository,
                    graphRepository = appGraph.graphRepository,
                    initialImportText = receivedImport?.text,
                    initialImportFileName = receivedImport?.fileName,
                    pendingOpenSessionId = pendingOpenSessionId,
                    onOpenSessionConsumed = { pendingOpenSessionId = null },
                    onExitRequested = ::finish
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureSystemBars() {
        val background = AndroidColor.rgb(244, 247, 253)
        window.statusBarColor = background
        window.navigationBarColor = background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receivedImportPayload = readImportPayload(intent)
        pendingOpenSessionId = readOpenSessionId(intent)
    }

    private fun readOpenSessionId(intent: Intent?): String? {
        if (intent == null) return null
        return intent.getStringExtra(AndroidBackgroundGenerationNotifier.EXTRA_OPEN_SESSION_ID)
            ?.takeIf { it.isNotBlank() }
    }

    private fun readImportPayload(intent: Intent?): ReceivedImportPayload? {
        if (intent == null) return null
        if (intent.action !in setOf(Intent.ACTION_VIEW, Intent.ACTION_SEND)) return null

        val directText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!directText.isNullOrBlank()) {
            return ReceivedImportPayload(
                fileName = "shared-import.json",
                text = directText
            )
        }

        val uri = intent.data ?: intent.streamExtraUri() ?: return null
        val text = contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return ReceivedImportPayload(
            fileName = uri.importFileName(),
            text = text
        )
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamExtraUri(): Uri? =
        getParcelableExtra(Intent.EXTRA_STREAM)

    private fun Uri.importFileName(): String =
        lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "received-import.json"
}

private data class ReceivedImportPayload(
    val fileName: String,
    val text: String
)
