package com.reversetutor.feature.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reversetutor.core.data.sources.SourceImportInput
import com.reversetutor.core.data.sources.SourceImportResult
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.data.sources.SourceWithChunks
import kotlinx.coroutines.launch

@Composable
fun SourcesRoute(
    sourceRepository: SourceRepository,
    pendingImport: SourceImportInput?,
    highlightedSourceId: String? = null,
    onPickSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf(emptyList<SourceWithChunks>()) }
    var lastImport by remember { mutableStateOf<SourceImportResult?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    fun reload() {
        refreshKey += 1
    }

    LaunchedEffect(refreshKey) {
        sources = sourceRepository.listSourcesWithChunks()
    }

    LaunchedEffect(pendingImport?.requestId) {
        val import = pendingImport ?: return@LaunchedEffect
        lastImport = sourceRepository.importSource(
            input = import,
            nowEpochMillis = System.currentTimeMillis()
        )
        reload()
    }

    SourcesScreen(
        state = SourcesUiState.from(sources = sources, lastImport = lastImport),
        highlightedSourceId = highlightedSourceId,
        onPickSource = onPickSource,
        onReprocess = { sourceId ->
            scope.launch {
                lastImport = sourceRepository.reprocessSource(
                    sourceId = sourceId,
                    nowEpochMillis = System.currentTimeMillis()
                )
                reload()
            }
        },
        modifier = modifier
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SourcesScreen(
    state: SourcesUiState,
    highlightedSourceId: String? = null,
    onPickSource: () -> Unit,
    onReprocess: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sources",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!highlightedSourceId.isNullOrBlank()) {
                    Text(
                        text = "Evidence target: $highlightedSourceId",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Button(onClick = onPickSource) {
                Text("Add source")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        ParserStatusLegend()
        val importStatus = state.importStatusLabel
        if (importStatus != null) {
            Spacer(modifier = Modifier.height(14.dp))
            ImportStatusPanel(status = importStatus, lines = state.importDetailLines)
        }
        Spacer(modifier = Modifier.height(18.dp))
        if (state.isEmpty) {
            EmptySources(onPickSource = onPickSource, title = state.emptyTitle)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.items.forEach { item ->
                    SourceCard(item = item, onReprocess = { onReprocess(item.id) })
                }
            }
        }
    }
}

@Composable
private fun ParserStatusLegend() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "Parser status", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "TXT and Markdown parse locally. HTML is sanitized as partial local extraction. PDF, DOCX, PPTX, EPUB, and image files stay visible as queued source material for assisted parsing or vision.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ImportStatusPanel(
    status: String,
    lines: List<String>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Last import: $status",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            lines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EmptySources(
    title: String,
    onPickSource: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Select TXT, Markdown, HTML, PDF, DOCX, PPTX, EPUB, image, or other files. Unsupported files remain visible with status.",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onPickSource) {
                Text("Choose file")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SourceCard(
    item: SourceCardUiItem,
    onReprocess: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(text = item.typeLabel, style = MaterialTheme.typography.bodyMedium)
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = item.statusLabel,
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Text(text = item.statusDetail, style = MaterialTheme.typography.bodyMedium)
            Text(text = item.chunkCountLabel, style = MaterialTheme.typography.bodyMedium)
            if (item.snippets.isNotEmpty()) {
                Text(
                    text = "Snippets",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                item.snippets.forEach { snippet ->
                    Text(text = snippet, style = MaterialTheme.typography.bodyMedium)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onReprocess,
                    modifier = Modifier.testTag("source-action-${item.id}")
                ) {
                    Text(item.actionLabel)
                }
            }
        }
    }
}
