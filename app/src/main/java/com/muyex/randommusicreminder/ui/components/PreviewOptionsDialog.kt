package com.muyex.randommusicreminder.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.domain.music.PreviewOptions

@Composable
fun PreviewOptionsDialog(initial: PreviewOptions, onSave: (PreviewOptions) -> Unit, onDismiss: () -> Unit) {
    var duration by rememberSaveable { mutableStateOf(initial.durationSeconds.toString()) }
    var volume by rememberSaveable { mutableStateOf(initial.volumePercent.toString()) }
    var start by rememberSaveable { mutableStateOf(initial.startSeconds.toString()) }
    val options = runCatching { PreviewOptions(duration.toInt(), volume.toInt(), start.toInt()) }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("试听参数") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("仅控制音乐库试听，不改变提醒音量或系统音量。音量百分比不等于实际响度。")
                listOf(Triple("试听时长（1～300 秒）", duration, 0), Triple("应用内音量（0～100%）", volume, 1),
                    Triple("起播位置（0～86400 秒）", start, 2)).forEach { (label, value, index) ->
                    OutlinedTextField(value = value, onValueChange = {
                        when (index) { 0 -> duration = it; 1 -> volume = it; else -> start = it }
                    }, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth())
                }
                if (options == null) Text("请填写范围内的整数。")
                Text("起点超过歌曲时长会提示错误；到时、离开音乐页或应用进入后台会停止。")
            }
        },
        confirmButton = { TextButton(enabled = options != null, onClick = { options?.let(onSave) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
