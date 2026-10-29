package com.muyex.randommusicreminder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.domain.settings.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(value: AppearanceSettings, onChange: (AppearanceSettings) -> Unit, modifier: Modifier = Modifier) {
    var custom by rememberSaveable(value.customColor) { mutableStateOf(value.customColor) }
    var error by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("外观偏好", style = MaterialTheme.typography.headlineSmall); Text("设置立即保存在本机，随参数备份导出。为保持可读性，极亮或极暗主色会派生对比色。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Text("明暗模式", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ThemeMode.entries.forEach { mode -> FilterChip(value.mode == mode, { onChange(value.copy(mode = mode)) }, label = { Text(mode.label) }) } }
        }
        item {
            HorizontalDivider(); Text("主题主色", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { ThemePalette.entries.forEach { palette ->
                FilterChip(value.palette == palette, { onChange(value.copy(palette = palette)) }, label = { Text(palette.label) }, leadingIcon = {
                    Box(Modifier.size(16.dp).background(Color(0xFF000000.toInt() or (if (palette == ThemePalette.CUSTOM) value.customColor else palette.hex).drop(1).toInt(16)), CircleShape))
                })
            } }
            if (value.palette == ThemePalette.CUSTOM) {
                OutlinedTextField(custom, { custom = it.take(7); error = null }, label = { Text("自定义主色 #RRGGBB") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { runCatching { value.copy(customColor = custom.uppercase()) }.onSuccess(onChange).onFailure { error = it.message } }) { Text("应用自定义颜色") }
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("主题预览", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("09:00  每个工作日", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("颜色应用于按钮、强调、选择状态和响铃页。日历班休继续使用独立语义颜色，避免混淆。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.medium) { Text("操作颜色示例", Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
        }
        item { TextButton(onClick = { onChange(AppearanceSettings()) }) { Text("恢复默认外观") } }
    }
}
