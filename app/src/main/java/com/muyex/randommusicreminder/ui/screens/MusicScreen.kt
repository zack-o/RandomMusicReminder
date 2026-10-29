package com.muyex.randommusicreminder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.muyex.randommusicreminder.ui.MusicListItem
import com.muyex.randommusicreminder.ui.MusicFilter
import com.muyex.randommusicreminder.ui.MusicSort
import com.muyex.randommusicreminder.ui.MusicImportProgress
import com.muyex.randommusicreminder.ui.filterMusic
import com.muyex.randommusicreminder.domain.music.PreviewOptions
import com.muyex.randommusicreminder.ui.components.PreviewOptionsDialog
import com.muyex.randommusicreminder.ui.components.MusicDetailsDialog
import com.muyex.randommusicreminder.ui.components.MusicArtwork
import com.muyex.randommusicreminder.ui.components.rememberEmbeddedMetadata

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicScreen(
    tracks: List<MusicListItem>,
    previewingTrackId: Long?,
    isImporting: Boolean,
    onImport: () -> Unit,
    onPreview: (Long) -> Unit,
    onStopPreview: () -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    previewOptions: PreviewOptions,
    onPreviewOptions: (PreviewOptions) -> Unit,
    onBulkToggle: (List<Long>, Boolean) -> Unit,
    importProgress: MusicImportProgress?,
    onDismissImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deletingTrack by remember { mutableStateOf<MusicListItem?>(null) }
    var detailsTrack by remember { mutableStateOf<MusicListItem?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(MusicFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(MusicSort.NEWEST) }
    var selectMode by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var sortOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var failuresOpen by remember { mutableStateOf(false) }
    val visible = remember(tracks, query, filter, sort) { filterMusic(tracks, query, filter, sort) }
    LaunchedEffect(tracks.map { it.id }) { selected = selected.filter { id -> tracks.any { it.id == id } } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "你的随机音乐库",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "启用的音乐会参与随机选择；响铃前可以随时试听。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onImport, enabled = !isImporting) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                Text(
                    text = if (isImporting) "正在导入…" else "添加本地音乐",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("搜索歌曲名称，空格分隔关键词") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MusicFilter.entries.forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    TextButton(onClick = { sortOpen = true }) { Text("排序：${sort.label}") }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        MusicSort.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { sort = option; sortOpen = false })
                        }
                    }
                }
                TextButton(onClick = { optionsOpen = true }) { Text("试听：${previewOptions.durationSeconds}秒 / ${previewOptions.volumePercent}%") }
                if (previewingTrackId != null) TextButton(onClick = onStopPreview) { Text("停止试听") }
                TextButton(onClick = { selectMode = !selectMode; selected = emptyList() }) { Text(if (selectMode) "退出选择" else "批量选择") }
            }
            Text("显示 ${visible.size} / ${tracks.size} 首", style = MaterialTheme.typography.labelMedium)
            if (selectMode) {
                Text("已选 ${selected.size} 首；切换筛选不会取消已选歌曲", style = MaterialTheme.typography.bodySmall)
                FlowRow {
                    TextButton(onClick = { selected = (selected + visible.map { it.id }).distinct() }) { Text("选中当前结果") }
                    TextButton(onClick = { selected = emptyList() }) { Text("清空选择") }
                    TextButton(enabled = selected.isNotEmpty(), onClick = { onBulkToggle(selected, true) }) { Text("启用所选") }
                    TextButton(enabled = selected.isNotEmpty(), onClick = { onBulkToggle(selected, false) }) { Text("停用所选") }
                }
            }
        }

        importProgress?.let { progress ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isImporting) "正在导入 ${progress.processed}/${progress.total}" else "上次导入结果")
                        if (isImporting) {
                            LinearProgressIndicator(progress = { progress.processed.toFloat() / progress.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                            Text(progress.currentName, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("成功 ${progress.imported} 首，失败 ${progress.failures.size} 首")
                        FlowRow {
                            if (progress.failures.isNotEmpty()) TextButton(onClick = { failuresOpen = true }) { Text("查看失败明细") }
                            if (!isImporting) TextButton(onClick = onDismissImport) { Text("收起结果") }
                        }
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Outlined.LibraryMusic, contentDescription = null)
                        Spacer(Modifier.height(8.dp))
                        Text("音乐库还是空的", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "导入手机里的音频文件后，每次提醒会自动换一首。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else if (visible.isEmpty()) {
            item { Text("没有符合条件的歌曲，请修改关键词或筛选条件。") }
        } else {
            items(visible, key = { it.id }) { track ->
                MusicCard(
                    track = track,
                    isPreviewing = previewingTrackId == track.id,
                    onPreview = { onPreview(track.id) },
                    onStopPreview = onStopPreview,
                    onToggle = { onToggle(track.id, it) },
                    onDelete = { deletingTrack = track },
                    onDetails = { detailsTrack = track },
                    selectMode = selectMode,
                    selected = track.id in selected,
                    onSelect = { checked -> selected = if (checked) (selected + track.id).distinct() else selected - track.id },
                )
            }
        }
    }

    if (optionsOpen) PreviewOptionsDialog(previewOptions, onSave = { onPreviewOptions(it); optionsOpen = false }, onDismiss = { optionsOpen = false })
    detailsTrack?.let { track -> MusicDetailsDialog(track, onDismiss = { detailsTrack = null }) }
    if (failuresOpen) AlertDialog(
        onDismissRequest = { failuresOpen = false }, title = { Text("导入失败明细") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(importProgress?.failures.orEmpty()) { failure ->
                    Column { Text(failure.fileName, fontWeight = FontWeight.SemiBold); Text(failure.reason, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }, confirmButton = { TextButton(onClick = { failuresOpen = false }) { Text("关闭") } },
    )

    deletingTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { deletingTrack = null },
            title = { Text("移除音乐？") },
            text = {
                Text(
                    "“${track.title}”会从随机音乐库中移除，不会删除手机中的原文件。" +
                        "音乐提醒会改用剩余音乐或乐醒内置兜底铃声；纯震动缺少节奏时使用震动预设，保持不发声。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(track.id)
                        deletingTrack = null
                    },
                ) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingTrack = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MusicCard(
    track: MusicListItem,
    isPreviewing: Boolean,
    onPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
    selectMode: Boolean,
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val metadata = rememberEmbeddedMetadata(track.uri)
    Card(Modifier.fillMaxWidth().clickable { if (selectMode) onSelect(!selected) else onDetails() }) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectMode) Checkbox(checked = selected, onCheckedChange = onSelect,
                modifier = Modifier.semantics { contentDescription = "选择${track.title}" })
            MusicArtwork(metadata, Modifier.size(44.dp))
            IconButton(
                onClick = if (isPreviewing) onStopPreview else onPreview,
                enabled = track.available,
            ) {
                Icon(
                    imageVector = if (isPreviewing) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                    contentDescription = if (isPreviewing) "停止试听" else "试听",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    track.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = track.enabled,
                onCheckedChange = onToggle,
                enabled = track.available || track.enabled,
            )
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "歌曲操作") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("封面、歌词与震动节奏") }, onClick = { menu = false; onDetails() })
                    DropdownMenuItem(text = { Text("移除应用副本") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
