package com.muyex.randommusicreminder.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muyex.randommusicreminder.music.*
import com.muyex.randommusicreminder.ui.MusicListItem

@Composable
fun rememberEmbeddedMetadata(uri: String): EmbeddedMusicMetadata {
    val context = LocalContext.current.applicationContext
    val metadata by produceState(EmbeddedMusicMetadata(), uri) {
        value = if (uri.isBlank()) EmbeddedMusicMetadata() else MusicMetadataReader.read(context, uri)
    }
    return metadata
}

@Composable
fun MusicArtwork(metadata: EmbeddedMusicMetadata, modifier: Modifier = Modifier) {
    Surface(modifier.clip(MaterialTheme.shapes.medium), color = MaterialTheme.colorScheme.secondaryContainer) {
        val artwork = metadata.artwork
        if (artwork != null) Image(artwork.asImageBitmap(), contentDescription = "内嵌专辑封面", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MusicNote, "没有内嵌封面", Modifier.fillMaxSize(.45f), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDetailsDialog(track: MusicListItem, onDismiss: () -> Unit) {
    val metadata = rememberEmbeddedMetadata(track.uri)
    var rhythmOpen by remember(track.id) { mutableStateOf(false) }
    if(rhythmOpen) HapticRhythmDialog(track) { rhythmOpen=false }
    else Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                TopAppBar(title = { Text("歌曲信息") }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭歌曲信息") } })
                Column(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MusicArtwork(metadata, Modifier.size(180.dp))
                    Text(metadata.title.ifBlank { track.title }, style = MaterialTheme.typography.titleLarge)
                    Text(listOfNotNull(metadata.artist.takeIf { it.isNotBlank() }, metadata.album.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "未找到歌手 / 专辑标签" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    metadata.issue?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick={rhythmOpen=true}) { Text("生成或查看震动节奏") }
                    HorizontalDivider()
                    LyricsPanel(metadata.lyrics, null, Modifier.weight(1f).fillMaxWidth())
                    Text("仅读取应用副本的内嵌信息，不播放、不联网，不修改原文件。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun LyricsPanel(document: LyricsDocument, positionMillis: Long?, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    val active = if (positionMillis == null) -1 else document.activeIndex(positionMillis)
    LaunchedEffect(active, follow) { if (follow && active >= 0) list.animateScrollToItem(active) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (document.lines.isEmpty()) "内嵌歌词" else "时间轴歌词", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (document.lines.isNotEmpty() && positionMillis != null) TextButton(onClick = { follow = !follow }) { Text(if (follow) "跟随播放" else "自由滚动") }
        }
        if (document.plainText.isBlank() && document.lines.isEmpty()) Text("未找到支持的内嵌歌词", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            val lines = if (document.lines.isNotEmpty()) document.lines.map { it.text } else remember(document.plainText) { document.plainText.lines() }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                itemsIndexed(lines) { index, text -> Text(text.ifBlank { "♪" }, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
