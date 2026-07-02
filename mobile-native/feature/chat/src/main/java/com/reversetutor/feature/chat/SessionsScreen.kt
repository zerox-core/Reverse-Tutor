package com.reversetutor.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.model.TutorSession
import com.reversetutor.core.protocol.NativeSessionPresetValidator
import kotlinx.coroutines.launch

@Composable
fun SessionsRoute(
    sessionRepository: SessionRepository,
    avatarVisible: Boolean,
    onOpenSession: (SessionListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf(emptyList<TutorSession>()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SessionListFilter.All) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var renameTarget by remember { mutableStateOf<SessionListItem?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionListItem?>(null) }
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var noticeText by remember { mutableStateOf<String?>(null) }

    fun reload() {
        refreshKey += 1
    }

    LaunchedEffect(refreshKey) {
        sessionRepository.ensurePreviewSeed(System.currentTimeMillis())
        sessions = sessionRepository.listSessions()
    }

    SessionsScreen(
        state = SessionListUiState.from(
            sessions = sessions.map { it.toSessionListItem(avatarVisible) },
            query = query,
            filter = filter,
            avatarVisible = avatarVisible
        ),
        onQueryChange = { query = it },
        onFilterChange = { filter = it },
        onOpenSession = onOpenSession,
        onNewSession = { showNewSessionDialog = true },
        onRenameSession = { renameTarget = it },
        onTogglePinned = { item ->
            scope.launch {
                sessionRepository.setPinned(
                    id = item.id,
                    pinned = !item.pinned,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
                reload()
            }
        },
        onDeleteSession = { deleteTarget = it },
        onExportSession = {
            noticeText = "Session export is deferred to import/export work."
        },
        onAvatarSession = {
            noticeText = "Per-session avatar is deferred to avatar/profile work."
        },
        modifier = modifier
    )

    if (showNewSessionDialog) {
        NewSessionDialog(
            onDismiss = { showNewSessionDialog = false },
            onCreate = { draft ->
                scope.launch {
                    sessionRepository.createSession(
                        input = draft.toCreationInput(),
                        nowEpochMillis = System.currentTimeMillis()
                    )
                    showNewSessionDialog = false
                    noticeText = if (draft.sourceHandoffRequested) {
                        "Initial source import is saved as a deferred handoff to Sources."
                    } else {
                        "Session created."
                    }
                    reload()
                }
            }
        )
    }

    val currentRenameTarget = renameTarget
    if (currentRenameTarget != null) {
        RenameSessionDialog(
            item = currentRenameTarget,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                scope.launch {
                    sessionRepository.renameSession(
                        id = currentRenameTarget.id,
                        title = title,
                        updatedAtEpochMillis = System.currentTimeMillis()
                    )
                    renameTarget = null
                    reload()
                }
            }
        )
    }

    val currentDeleteTarget = deleteTarget
    if (currentDeleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete session") },
            text = { Text("Delete ${currentDeleteTarget.title}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            sessionRepository.archiveSession(
                                id = currentDeleteTarget.id,
                                updatedAtEpochMillis = System.currentTimeMillis()
                            )
                            deleteTarget = null
                            reload()
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val currentNotice = noticeText
    if (currentNotice != null) {
        AlertDialog(
            onDismissRequest = { noticeText = null },
            title = { Text("Deferred action") },
            text = { Text(currentNotice) },
            confirmButton = {
                TextButton(onClick = { noticeText = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun SessionsScreen(
    state: SessionListUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (SessionListFilter) -> Unit,
    onOpenSession: (SessionListItem) -> Unit,
    onNewSession: () -> Unit,
    onRenameSession: (SessionListItem) -> Unit,
    onTogglePinned: (SessionListItem) -> Unit,
    onDeleteSession: (SessionListItem) -> Unit,
    onExportSession: (SessionListItem) -> Unit,
    onAvatarSession: (SessionListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sessions",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            Button(onClick = onNewSession) {
                Text("New session")
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search sessions") }
        )
        Spacer(modifier = Modifier.height(10.dp))
        SessionFilterChips(
            selected = state.filter,
            onFilterChange = onFilterChange
        )
        Spacer(modifier = Modifier.height(14.dp))
        if (state.visibleSessions.isEmpty()) {
            EmptySessions(title = state.emptyStateTitle)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.visibleSessions, key = { it.id }) { item ->
                    SessionCard(
                        item = item,
                        onOpen = { onOpenSession(item) },
                        onRename = { onRenameSession(item) },
                        onTogglePinned = { onTogglePinned(item) },
                        onDelete = { onDeleteSession(item) },
                        onExport = { onExportSession(item) },
                        onAvatar = { onAvatarSession(item) }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NewSessionDialog(
    onDismiss: () -> Unit,
    onCreate: (NewSessionDraft) -> Unit
) {
    var draft by remember {
        mutableStateOf(
            NewSessionDraft(
                title = "",
                role = "",
                goal = "",
                profileText = ""
            )
        )
    }
    var presetJson by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New session") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Templates",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BuiltInSessionTemplates.all.forEach { template ->
                        TextButton(
                            onClick = {
                                draft = NewSessionDraft.fromTemplate(template)
                                errorText = null
                            }
                        ) {
                            Text(template.title)
                        }
                    }
                }
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.role,
                    onValueChange = { draft = draft.copy(role = it) },
                    singleLine = true,
                    label = { Text("Role") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.goal,
                    onValueChange = { draft = draft.copy(goal = it) },
                    singleLine = true,
                    label = { Text("Goal") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.profileText,
                    onValueChange = { draft = draft.copy(profileText = it) },
                    minLines = 3,
                    label = { Text("Profile") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = draft.sourceHandoffRequested,
                        onCheckedChange = { checked ->
                            draft = draft.copy(sourceHandoffRequested = checked)
                        }
                    )
                    Text(
                        text = "Prepare source import after create",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                OutlinedTextField(
                    value = presetJson,
                    onValueChange = { presetJson = it },
                    minLines = 3,
                    label = { Text("Preset JSON") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = presetJson.isNotBlank(),
                    onClick = {
                        val result = NativeSessionPresetValidator.validate(presetJson)
                        val preset = result.preset
                        if (result.isValid && preset != null) {
                            onCreate(NewSessionDraft.fromPreset(preset))
                        } else {
                            errorText = result.errors.joinToString("\n")
                        }
                    }
                ) {
                    Text("Create from preset")
                }
                val currentError = errorText
                if (currentError != null) {
                    Text(
                        text = currentError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.validationErrors().isEmpty(),
                onClick = { onCreate(draft) }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SessionFilterChips(
    selected: SessionListFilter,
    onFilterChange: (SessionListFilter) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == SessionListFilter.All,
            onClick = { onFilterChange(SessionListFilter.All) },
            label = { Text("All") }
        )
        FilterChip(
            selected = selected == SessionListFilter.Pinned,
            onClick = { onFilterChange(SessionListFilter.Pinned) },
            label = { Text("Pinned") }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SessionCard(
    item: SessionListItem,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onAvatar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.statusLabel} | ${item.unreadLabel} | ${item.avatarLabel}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                if (item.pinned) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Pinned",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(onClick = onOpen) {
                    Text("Open")
                }
                TextButton(onClick = onRename) {
                    Text("Rename")
                }
                TextButton(onClick = onTogglePinned) {
                    Text(if (item.pinned) "Unpin" else "Pin")
                }
                TextButton(onClick = onAvatar) {
                    Text("Avatar")
                }
                TextButton(onClick = onExport) {
                    Text("Export")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun EmptySessions(title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(18.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RenameSessionDialog(
    item: SessionListItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename session") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Title") }
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.trim().isNotEmpty(),
                onClick = { onConfirm(title) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
