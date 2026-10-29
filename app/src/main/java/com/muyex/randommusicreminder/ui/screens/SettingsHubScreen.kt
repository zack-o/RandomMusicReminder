package com.muyex.randommusicreminder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.ui.*

@Composable
fun SettingsHubScreen(settings: SettingsUi, onOpen: (AppSection) -> Unit, modifier: Modifier = Modifier, trashCount: Int = 0) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("设置中心", style = MaterialTheme.typography.headlineSmall) }
        item { HubHeading("偏好与运行") }
        item { HubRow(Icons.Outlined.Palette, "外观与主题", "${settings.appearance.mode.label} · ${settings.appearance.palette.label}") { onOpen(AppSection.APPEARANCE) } }
        item { HubRow(Icons.Outlined.NotificationsActive, "提醒与权限", "默认响铃、稍后提醒、通知与后台健康") { onOpen(AppSection.REMINDER_SETTINGS) } }
        item { HubHeading("智能与记录") }
        item { HubRow(Icons.Outlined.AutoAwesome, "AI助手", "连接服务、文字识别与统计解读") { onOpen(AppSection.AI) } }
        item { HubRow(Icons.Outlined.History, "响铃历史", "查看何时响铃、播放曲目和处理结果") { onOpen(AppSection.HISTORY) } }
        item { HubRow(Icons.Outlined.BarChart, "数据统计", "本应用使用时长、打开次数与响铃汇总") { onOpen(AppSection.STATISTICS) } }
        item { HubRow(Icons.Outlined.TaskAlt, "完成记录", "完成与撤销记录，和停止提示分开") { onOpen(AppSection.COMPLETIONS) } }
        item { HubHeading("数据管理") }
        item { HubRow(Icons.AutoMirrored.Outlined.Label, "标签管理", "统一改名与整理分类，事项继续保留") { onOpen(AppSection.TAGS) } }
        item { HubRow(Icons.Outlined.DeleteOutline, "回收站", "${trashCount}项，可恢复；永久删除需单独确认") { onOpen(AppSection.TRASH) } }
        item { HubRow(Icons.Outlined.ImportExport, "导入导出", "事件、日历、参数备份与统计CSV") { onOpen(AppSection.DATA) } }
        item { Text("乐醒 ${com.muyex.randommusicreminder.BuildConfig.VERSION_NAME}\n提醒、音乐与日历在本机运行，AI仅确认后联网。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp)) }
    }
}

@Composable private fun HubHeading(text: String) { Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp)) }
@Composable private fun HubRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
