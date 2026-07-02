package com.reversetutor.preview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.preferences.AppPreferences
import com.reversetutor.preview.shell.AppShell
import com.reversetutor.preview.theme.ReverseTutorTheme

class MainActivity : ComponentActivity() {
    private var receivedImportPayload by mutableStateOf<ReceivedImportPayload?>(null)

    private val appPreferencesRepository by lazy {
        DataModule.appPreferencesRepository(this)
    }
    private val sessionRepository by lazy {
        DataModule.sessionRepository(this)
    }
    private val messageRepository by lazy {
        DataModule.messageRepository(this)
    }
    private val llmProfileRepository by lazy {
        DataModule.llmProfileRepository(this)
    }
    private val chatGenerationRepository by lazy {
        DataModule.chatGenerationRepository(this)
    }
    private val sourceRepository by lazy {
        DataModule.sourceRepository(this)
    }
    private val memoryRepository by lazy {
        DataModule.memoryRepository(this)
    }
    private val graphRepository by lazy {
        DataModule.graphRepository(this)
    }
    private val localDataWipeRepository by lazy {
        DataModule.localDataWipeRepository(this)
    }
    private val nativeImportRepository by lazy {
        DataModule.nativeImportRepository(this)
    }
    private val nativeExportRepository by lazy {
        DataModule.nativeExportRepository(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receivedImportPayload = readImportPayload(intent)
        setContent {
            val appPreferences by appPreferencesRepository.preferences.collectAsState(
                initial = AppPreferences.defaults
            )
            val receivedImport = receivedImportPayload

            ReverseTutorTheme {
                AppShell(
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
                    initialImportText = receivedImport?.text,
                    initialImportFileName = receivedImport?.fileName,
                    onExitRequested = ::finish
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receivedImportPayload = readImportPayload(intent)
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
