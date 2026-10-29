package com.muyex.randommusicreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.ui.ToolsState
import com.muyex.randommusicreminder.ui.components.ImportPreviewCard

@Composable
fun DataScreen(state: ToolsState, onExport: (Boolean) -> Unit, onCsv: () -> Unit, onImport: () -> Unit,
    onMerge: (Boolean) -> Unit, onSettings: () -> Unit, onDismiss: () -> Unit, onRecovery: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("数据与参数", style = MaterialTheme.typography.headlineSmall)
            Text("JSON 保存事件、提醒点、自定义日历及设置；不含歌曲文件、密钥、AI配置、历史和系统排程。统计可另导CSV。导出文件可能含私人信息，请妥善保管。")
            Text("普通备份只包含正常事项及标签（含停用事项），不包含回收站。需要导出的回收事项请先恢复，恢复后可保持停用。",style=MaterialTheme.typography.bodySmall)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !state.busy, onClick = { onExport(false) }) { Text("导出核心数据 JSON") }
                OutlinedButton(enabled = !state.busy, onClick = { onExport(true) }) { Text("仅导出设置 JSON") }
                OutlinedButton(enabled = !state.busy, onClick = onCsv) { Text("导出历史与统计 CSV") }
                OutlinedButton(enabled = !state.busy, onClick = onRecovery) { Text("导出最近一次导入前快照") }
                Button(enabled = !state.busy, onClick = onImport) { Text("选择 JSON 并预览") }
                Text("只合并新增，不整库覆盖。与正常或回收事项重复的安排会跳过，改变标签不会重新创建事项；自选歌曲须在本机唯一匹配名称、时长和大小，否则整条事件跳过。导入上限2 MiB、1000事件、5000日期。", style = MaterialTheme.typography.bodySmall)
                Text("应用设置或合并前自动保存快照，可由上方按钮导出；不含密钥或音乐。重新导入仍采用合并，不能自动删除已新增事件。设置与数据分开确认。", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.importPreview?.let { preview -> item { ImportPreviewCard(preview, state.busy, false, onMerge, onSettings, onDismiss) } }
    }
}
