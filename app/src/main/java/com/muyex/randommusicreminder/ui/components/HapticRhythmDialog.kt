package com.muyex.randommusicreminder.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.muyex.randommusicreminder.domain.haptics.*
import com.muyex.randommusicreminder.music.*
import com.muyex.randommusicreminder.playback.ReminderHaptics
import com.muyex.randommusicreminder.ui.MusicListItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HapticRhythmDialog(track: MusicListItem, onDismiss: ()->Unit) {
    val context=LocalContext.current.applicationContext
    val store=remember(context) { HapticPatternStore(context) }
    val output=remember(context) { ReminderHaptics.create(context) }
    val scope=rememberCoroutineScope();val lifecycle=LocalLifecycleOwner.current.lifecycle
    var result by remember(track.id) { mutableStateOf<CachedHapticRhythm?>(null) }
    var start by remember(track.id) { mutableStateOf("0") }
    var length by remember(track.id) { mutableStateOf("10") }
    var message by remember(track.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var generation by remember { mutableStateOf<Job?>(null) }
    var preview by remember { mutableStateOf<Job?>(null) }
    var confirmPreview by remember { mutableStateOf(false) }
    LaunchedEffect(track.id,track.uri) {
        (context as? com.muyex.randommusicreminder.app.LexingApplication)?.container?.musicPreviewController?.stop()
        val cached=store.read(track.id.toString(),track.uri);if(generation==null) result=cached
    }
    DisposableEffect(lifecycle,track.id) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) { generation?.cancel();preview?.cancel();output.stop() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);generation?.cancel();preview?.cancel();output.stop() }
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("从音乐生成震动节奏")},text={
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(track.title,style=MaterialTheme.typography.titleMedium)
            Text("只解码本机音乐副本，不播放、不录音、不联网。每首保留一份最新成功结果，生成失败或取消会保留原缓存。",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(start,{start=it.take(5)},label={Text("片段起点（秒）")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            OutlinedTextField(length,{length=it.take(2)},label={Text("片段时长（10～30秒）")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            if(busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick={generation?.cancel();message="已取消，原缓存保留"}) { Text("取消生成") }
            } else Button(onClick={
                val offset=start.toIntOrNull();val duration=length.toIntOrNull()
                if(offset==null || offset !in 0..86400 || duration==null || duration !in 10..30 || offset*1000L+duration*1000L>track.durationMillis) message="请选择曲目范围内10～30秒片段"
                else {
                    busy=true;message=null;preview?.cancel();output.stop()
                    generation=scope.launch {
                        try { result=store.generate(track.id.toString(),track.uri,offset,duration);message="生成完成，可作为提醒节奏使用" }
                        catch(error: CancellationException) { throw error }
                        catch(_: Exception) { message="未生成有效节奏：片段可能过静、能量变化不足或格式不支持。请换片段或使用标准预设；原缓存保留。" }
                        finally { busy=false }
                    }
                }
            }) { Text(if(result==null) "生成节奏" else "重新生成") }
            result?.let { cached ->
                Text("已保存：起点${cached.startSeconds}秒，${cached.pattern.durationMillis/1000}秒，${cached.pattern.pulses.size}次能量脉冲")
                val color=MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription="片段能量脉冲示意，并非精确节拍" }) {
                    cached.pattern.pulses.forEach { pulse ->
                        val height=size.height*pulse.amplitude/255f
                        drawRect(color,Offset(size.width*pulse.startMillis/cached.pattern.durationMillis,size.height-height),Size((size.width*pulse.durationMillis/cached.pattern.durationMillis).coerceAtLeast(1f),height))
                    }
                }
                OutlinedButton(enabled=!busy,onClick={confirmPreview=true}) { Text("试震最多2秒") }
                TextButton(onClick={preview?.cancel();output.stop();message="试震已停止"}) { Text("停止试震") }
            }
            Text(output.capability,style=MaterialTheme.typography.bodySmall)
            Text("提取的是能量变化，不代表精确节拍或音高。生成后，在事项提醒方式中开启“使用音乐生成的节奏”。",style=MaterialTheme.typography.bodySmall)
            message?.let { Text(it,style=MaterialTheme.typography.bodyMedium) }
        }
    },confirmButton={TextButton(onClick=onDismiss) { Text("关闭") }})
    if(confirmPreview) AlertDialog(onDismissRequest={confirmPreview=false},title={Text("试震最多2秒？")},text={Text("将使用此设备的震动马达，不播放音乐。触感取决于马达及系统设置；可随时停止，离开页面或进入后台也会停止。")},
        confirmButton={TextButton(onClick={
            confirmPreview=false
            val pattern=result?.pattern
            if(pattern!=null) { preview?.cancel();preview=scope.launch { try { message=output.start(pattern,HapticSettings());delay(2000) } finally { output.stop() } } }
        }) { Text("开始试震") }},dismissButton={TextButton(onClick={confirmPreview=false}) { Text("取消") }})
}
