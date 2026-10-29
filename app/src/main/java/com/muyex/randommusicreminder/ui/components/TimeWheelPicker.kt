package com.muyex.randommusicreminder.ui.components

import android.os.Build
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.LocalTime
import java.util.Locale

@Composable
fun TimeWheelPicker(value: LocalTime, allowSeconds: Boolean, onChange: (LocalTime) -> Unit) {
    var secondsVisible by remember { mutableStateOf(value.second != 0) }
    LaunchedEffect(value.second) { if (value.second != 0) secondsVisible = true }
    val change by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(value)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            WheelColumn("小时", 23, value.hour, Modifier.weight(1f)) { change(current.withHour(it)) }
            Text(":", style = MaterialTheme.typography.headlineMedium)
            WheelColumn("分钟", 59, value.minute, Modifier.weight(1f)) { change(current.withMinute(it)) }
            if (allowSeconds && secondsVisible) {
                Text(":", style = MaterialTheme.typography.headlineMedium)
                WheelColumn("秒", 59, value.second, Modifier.weight(1f)) { change(current.withSecond(it)) }
            }
        }
        if (allowSeconds) Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("设置秒", style = MaterialTheme.typography.bodyMedium)
                Text("关闭后秒归零；设置到秒不代表系统保证按秒送达", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(secondsVisible, { visible -> secondsVisible = visible; if (!visible) change(current.withSecond(0).withNano(0)) }, modifier = Modifier.testTag("clock_seconds_toggle"))
        }
    }
}

@Composable
private fun WheelColumn(label: String, maximum: Int, value: Int, modifier: Modifier = Modifier, onValue: (Int) -> Unit) {
    val changed by rememberUpdatedState(onValue)
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(modifier = Modifier.widthIn(max = 82.dp).fillMaxWidth().height(160.dp).testTag("clock_wheel_$label"), factory = { context ->
            NumberPicker(context).apply {
                minValue = 0; maxValue = maximum; wrapSelectorWheel = true
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setFormatter { String.format(Locale.ROOT, "%02d", it) }
                contentDescription = label
                setOnValueChangedListener { _, _, new -> changed(new) }
            }
        }, update = { picker ->
            if (picker.value != value) picker.value = value
            if (Build.VERSION.SDK_INT >= 29) picker.textColor = ink
            for (index in 0 until picker.childCount) (picker.getChildAt(index) as? TextView)?.setTextColor(ink)
        })
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
