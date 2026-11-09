package com.hebeu.emptyclassroom.ui.query

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebeu.emptyclassroom.domain.model.Classroom
import com.hebeu.emptyclassroom.domain.model.lookupKey
import java.time.LocalDateTime
import com.hebeu.emptyclassroom.ui.result.ClassroomDetailDialog
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun QuickLookupRoute(viewModel: QuickLookupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh(false) }
    LaunchedEffect(viewModel) {
        while (true) { viewModel.refresh(false); delay(60_000) }
    }
    QuickLookupCard(state, onRefresh = { viewModel.refresh() }, onMinimum = viewModel::minimumMinutes)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickLookupCard(state: QuickLookupState, onRefresh: () -> Unit, onMinimum: (Int) -> Unit = {}) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Classroom?>(null) }
    selected?.let { ClassroomDetailDialog(it, onDismiss = { selected = null }, availability = state.availability[it.lookupKey()]?.label) }
    var now by remember { mutableStateOf(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")); delay(60_000) } }
    val visible = state.rooms.filter { state.minMinutes == 0 || (state.availability[it.lookupKey()]?.minutesFrom(now) ?: 0) >= state.minMinutes }
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Bolt, null)
                Text("当前空闲教室速查", style = MaterialTheme.typography.titleLarge)
            }
            Text("新校区 · 基础一、二", style = MaterialTheme.typography.labelLarge)
            Text(state.description, style = MaterialTheme.typography.bodyMedium)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.completed) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "当前空闲", 60 to "至少 1 小时", 120 to "至少 2 小时").forEach { (minutes, label) ->
                        FilterChip(selected = state.minMinutes == minutes, onClick = { onMinimum(minutes) }, label = { Text(label) })
                    }
                }
                if (state.durationLoading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在核对连续空闲时长…", style = MaterialTheme.typography.labelSmall) }
                state.durationError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(if (visible.isEmpty()) { if (state.minMinutes == 0) "该时段暂无空闲教室" else if (state.durationLoading) "正在筛选满足时长的教室…" else "暂未找到满足时长的教室" } else "找到 ${visible.size} 间空闲教室",
                    style = MaterialTheme.typography.titleMedium)
                visible.groupBy { it.buildingName ?: "教学楼" }.forEach { (building, rooms) ->
                    Text("$building · ${rooms.size} 间", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (if (expanded) rooms else rooms.take(3)).forEach { room ->
                            SuggestionChip(onClick = { selected = room }, label = { Text(room.name + state.availability[room.lookupKey()]?.let { " · ${it.label}" }.orEmpty()) })
                        }
                    }
                }
                if (visible.groupBy { it.buildingName }.any { it.value.size > 3 }) TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "查看全部 ${visible.size} 间")
                }
                state.queriedAt?.let {
                    Text("更新于 " + DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Shanghai"))
                        .format(Instant.ofEpochMilli(it)), style = MaterialTheme.typography.labelSmall)
                }
            }
            FilledTonalButton(onClick = onRefresh, enabled = !state.loading && !state.durationLoading) {
                Text(if (state.completed) "刷新速查" else "立即速查")
            }
        }
    }
}
