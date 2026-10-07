package com.reversetutor.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatReferenceQueryRoute(
    sessionId: String,
    queryPort: ChatReferenceQueryPort,
    initialState: ChatReferenceQueryState = ChatReferenceQueryState(),
    onStateChanged: (ChatReferenceQueryState) -> Unit = {},
    onNavigate: (ChatQueryNavigationRequest) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val coordinator = remember(sessionId, queryPort) {
        ChatReferenceQueryCoordinator(
            sessionId = sessionId,
            queryPort = queryPort,
            scope = scope,
            initialState = initialState,
            onStateChanged = onStateChanged,
            onNavigate = onNavigate
        )
    }
    val state = coordinator.state
    DisposableEffect(coordinator) { onDispose(coordinator::close) }

    LaunchedEffect(sessionId) { onStateChanged(state) }

    ChatReferenceQueryScreen(
        state = state,
        onBack = onBack,
        onQueryChange = coordinator::onQueryChange,
        onSubmit = coordinator::submit,
        onScopeChange = coordinator::onScopeChange,
        onCategoryChange = { coordinator.updateCategory(it) },
        onResultClick = { resultId -> state.select(resultId)?.let(onNavigate) },
        onRetry = coordinator::submit,
        modifier = modifier
    )
}

@Composable
fun ChatReferenceQueryScreen(
    state: ChatReferenceQueryState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onScopeChange: (ChatQueryScope) -> Unit,
    onCategoryChange: (ChatQueryCategory) -> Unit,
    onResultClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(Color(0xFFF4F7FC))) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "返回聊天")
            }
            Text("资料与引用", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                BasicTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFF171C27), fontSize = 15.sp),
                    cursorBrush = SolidColor(Color(0xFF2979D6)),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSubmit) { Text("查询") }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("全部会话", modifier = Modifier.weight(1f), fontSize = 14.sp)
            Switch(
                checked = state.scope == ChatQueryScope.AllSessions,
                onCheckedChange = {
                    onScopeChange(if (it) ChatQueryScope.AllSessions else ChatQueryScope.CurrentSession)
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChatQueryCategory.entries.forEach { category ->
                FilterChip(
                    selected = state.selectedCategory == category,
                    onClick = { onCategoryChange(category) },
                    label = { Text(category.label) }
                )
            }
        }
        when (val load = state.loadState) {
            ChatQueryLoadState.Idle -> QueryMessage("输入关键词查询当前会话")
            ChatQueryLoadState.Loading -> QueryMessage("正在查询...")
            ChatQueryLoadState.Empty -> QueryMessage("没有找到匹配结果")
            is ChatQueryLoadState.Failure -> QueryError(load.message, onRetry)
            is ChatQueryLoadState.Offline -> QueryResults(
                results = state.results.filter { it.category == state.selectedCategory },
                offline = true,
                selectionRequired = state.selectionRequired,
                onResultClick = onResultClick
            )
            is ChatQueryLoadState.Ready -> QueryResults(
                results = state.results.filter { it.category == state.selectedCategory },
                offline = false,
                selectionRequired = state.selectionRequired,
                onResultClick = onResultClick
            )
        }
    }
}

@Composable
private fun QueryResults(
    results: List<ChatQueryResult>,
    offline: Boolean,
    selectionRequired: Boolean,
    onResultClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        if (offline) {
            item { Text("离线 · 显示本地可用结果", color = Color(0xFF7A5B16), modifier = Modifier.padding(vertical = 10.dp)) }
        } else if (selectionRequired) {
            item { Text("请选择要定位的结果", color = Color(0xFF59647A), modifier = Modifier.padding(vertical = 10.dp)) }
        }
        if (results.isEmpty()) {
            item { QueryMessage("此分类暂无结果") }
        } else {
            items(results, key = { it.id }) { result ->
                Surface(
                    onClick = { onResultClick(result.id) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(result.summary, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(result.sourceIdentity, color = Color(0xFF6D778C), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFF6D778C), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun QueryError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = Color(0xFF9D3340))
        TextButton(onClick = onRetry) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
            Text("重试")
        }
    }
}
