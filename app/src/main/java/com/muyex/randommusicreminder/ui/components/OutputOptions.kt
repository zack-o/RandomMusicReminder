package com.muyex.randommusicreminder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.domain.haptics.*
import com.muyex.randommusicreminder.playback.ReminderHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutputOptions(mode: ReminderOutputMode, settings: HapticSettings, onChange: (ReminderOutputMode,HapticSettings)->Unit) {
    val context=LocalContext.current
    val capability=remember(context) { ReminderHaptics.create(context).capability }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("提醒方式",style=MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ReminderOutputMode.entries.forEach { value -> FilterChip(mode==value,{onChange(value,settings)},label={Text(value.label)}) }
        }
        if(!mode.audio) Text(if(mode.vibration) "只请求震动；音乐缺失或震动失败时保留通知，不切换成声音。" else "仅显示无声通知，不播放音乐、不请求震动，也不自动打开全屏。",style=MaterialTheme.typography.bodySmall)
        if(mode.vibration) {
            Text(capability,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("标准震动 / 缓存缺失时的预设",style=MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { HapticPreset.entries.forEach { value ->
                FilterChip(settings.preset==value,{onChange(mode,settings.copy(preset=value))},label={Text(value.label)})
            } }
            Text("强度",style=MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { HapticStrength.entries.forEach { value ->
                FilterChip(settings.strength==value,{onChange(mode,settings.copy(strength=value))},label={Text(value.label)})
            } }
            Text("循环间隔 ${settings.repeatGapMillis}毫秒",style=MaterialTheme.typography.labelLarge)
            Slider(settings.repeatGapMillis.toFloat(),{onChange(mode,settings.copy(repeatGapMillis=(it/100).toInt()*100))},valueRange=500f..5000f,steps=44)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("使用音乐生成的节奏");Text("先在音乐详情生成片段缓存；缺失时使用上方预设。",style=MaterialTheme.typography.bodySmall) }
                Switch(settings.useMusicRhythm,{onChange(mode,settings.copy(useMusicRhythm=it))})
            }
            Text("节奏按片段能量变化生成；音乐加震动时独立循环，暂不保证逐拍同步。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
