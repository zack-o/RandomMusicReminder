package com.muyex.randommusicreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muyex.randommusicreminder.ai.AiConfig
import com.muyex.randommusicreminder.ui.*
import com.muyex.randommusicreminder.ui.components.ImportPreviewCard
import java.net.URI
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiScreen(state: ToolsState, onSave: (AiConfig, String) -> Unit, onClear: () -> Unit,
    onPrepare: (AiAction, String) -> Unit, onSend: () -> Unit, onDismissRequest: () -> Unit, onCancel: () -> Unit,
    onMerge: (Boolean) -> Unit, onDismissDraft: () -> Unit, modifier: Modifier = Modifier,
    onSelectProfile: (String) -> Unit = {}, onScopeChange: (Set<Long>) -> Unit = {}, onTrackScopeChange: (Set<Long>) -> Unit = {},
    onApplyEdit: () -> Unit = {}, onUndo: (String) -> Unit = {}) {
    var showConfig by rememberSaveable { mutableStateOf(false) }
    var configDraft by remember { mutableStateOf<AiConfig?>(null) }
    var action by rememberSaveable { mutableStateOf(AiAction.TASKS) }
    var undoId by remember { mutableStateOf<String?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var clearKey by remember { mutableStateOf(false) }
    val ready = state.config.enabled && state.hasKey && state.config.model.isNotBlank() && state.config.endpoint.isNotBlank()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("把安排，说清楚就好", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("创建、修改、解释与复盘；每步先核对", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.primary)
                        Text(if (ready) "连接已配置" else "先连接你的 AI 服务", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                        TextButton(enabled = !state.busy, onClick = { configDraft = state.config; showConfig = true }) { Text("管理连接") }
                    }
                    Text(if (ready) state.config.model else "密钥仅保存在本机，保存配置不会联网", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (state.config.endpoint.isNotBlank()) {
                        Text(runCatching { URI(state.config.endpoint).host }.getOrDefault("自定义服务"), style = MaterialTheme.typography.bodySmall)
                        Text("实际请求：${state.config.requestEndpoint}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!state.config.enabled) Text("AI 当前关闭，原有提醒照常离线运行", style = MaterialTheme.typography.bodySmall)
                    else if (!state.hasKey) Text("尚未保存 API Key，请在管理连接中填写", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = ready && !state.busy, onClick = { onPrepare(AiAction.TEST, "") }) {
                        Icon(Icons.Outlined.Science, null, Modifier.size(18.dp))
                        Text("测试连接", Modifier.padding(start = 8.dp))
                    }
                    Text("测试也可能计费。每次请求先确认发送内容，不自动重试。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("连接档案", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.profiles.forEach { profile -> FilterChip(selected = profile.profileId == state.config.profileId, enabled = !state.busy,
                    onClick = { onSelectProfile(profile.profileId) }, label = { Text(profile.profileName) }) }
                AssistChip(enabled = !state.busy && state.profiles.size < 20, onClick = {
                    configDraft = AiConfig(profileId = java.util.UUID.randomUUID().toString(), profileName = "新连接"); showConfig = true
                }, label = { Text("新增连接") })
            }
        }
        state.aiFeedback?.let { feedback -> item {
            Surface(color = if (feedback.success) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (feedback.success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null)
                        Text(feedback.title, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                        Text(String.format(Locale.ROOT, "%.1f 秒", feedback.elapsedMillis / 1000.0), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(feedback.detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("任务工作台", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiAction.entries.filter { it !in setOf(AiAction.TEST, AiAction.STATISTICS) }.forEach { value ->
                        FilterChip(action == value, { action = value }, enabled = !state.busy, label = { Text(value.label) })
                    }
                }
                if (action in setOf(AiAction.EDIT, AiAction.EXPLAIN, AiAction.CONFLICTS)) {
                    Text("明确选择提醒 · 已选${state.eventScope.size}/20", style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        state.aiEvents.forEach { event -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(event.event.id in state.eventScope, { checked -> onScopeChange(if (checked) state.eventScope + event.event.id else state.eventScope - event.event.id) }, enabled = !state.busy && (event.event.id in state.eventScope || state.eventScope.size < 20))
                            Text("#${event.event.id} ${event.event.title}", style = MaterialTheme.typography.bodyMedium)
                        } }
                    }
                    Text("发送所选标题和规则，不含备注、音乐文件或其他事件；解释/冲突会附带所选已安排时间。", style = MaterialTheme.typography.bodySmall)
                }
                if (action == AiAction.MUSIC) {
                    Text("选择音乐元信息 · 已选${state.trackScope.size}/50", style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        state.aiTracks.forEach { track -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(track.id in state.trackScope, { checked -> onTrackScopeChange(if (checked) state.trackScope + track.id else state.trackScope - track.id) }, enabled = !state.busy && (track.id in state.trackScope || state.trackScope.size < 50))
                            Text(track.displayName, style = MaterialTheme.typography.bodyMedium)
                        } }
                    }
                    Text("仅发送勾选曲名、时长与可用状态；AI没有听过音频，不直接更改音乐池。", style = MaterialTheme.typography.bodySmall)
                }
                Text("安排输入", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = input, onValueChange = { input = it.take(12_000) }, label = { Text("描述你的安排") },
                    placeholder = { Text("下周三下午3点开会，提前30分钟和准时提醒。") },
                    minLines = 4, maxLines = 8, enabled = !state.busy,
                    supportingText = { Text("${input.length} / 12000 字 · 可粘贴多项安排或放假通知") },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                Text("试试这样说", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("轮班安排" to "从明天起白夜休休四天轮换，白班08:30:00提醒、夜班19:30:00提醒，两个休息日不提醒，准时提示一次。",
                        "抢票开售" to "明天09:59:55提醒我演唱会开售，提前5分钟和准时各提醒一次，备注提前登录购票账号。",
                        "随机窗口" to "从明天起每天8:00到8:10之间随机响一次，温和提醒30秒，关闭稍后和补充提醒。",
                        "多次提醒" to "下周三15点开会，提前30分钟和准时提醒。",
                        "工作日安排" to "从明天起每个法定工作日9点提醒喝水，使用温和提醒。",
                        "自定义假期" to "将明年1月4日设为我的休息日，备注个人休假。").forEach { (label, example) ->
                        SuggestionChip(enabled = !state.busy, onClick = { input = example }, label = { Text(label) })
                    }
                }
                Button(enabled = ready && !state.busy && (input.isNotBlank() || action in setOf(AiAction.EXPLAIN, AiAction.CONFLICTS, AiAction.MUSIC)), onClick = { onPrepare(action, input) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(20.dp))
                    Text(if (action in setOf(AiAction.TASKS, AiAction.EDIT)) "生成待核对草稿" else action.label, Modifier.padding(start = 8.dp))
                }
                Text("按任务发送上方选择的范围；发送前展示全文与地址。AI 返回先预览，解释和建议不会修改数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.busy) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (state.aiRunning) "正在等待服务返回，请勿重复发送" else "正在处理", style = MaterialTheme.typography.bodySmall)
                if (state.aiRunning) OutlinedButton(onClick = onCancel) { Text("取消请求") }
            }
        }
        if (state.aiText.isNotBlank()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.aiPreview != null) "识别说明" else "服务返回", style = MaterialTheme.typography.titleMedium)
                Text(state.aiText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        state.aiPreview?.let { preview -> item { ImportPreviewCard(preview, state.busy, true, onMerge, null, onDismissDraft) } }
        state.editProposal?.let { proposal -> item { com.muyex.randommusicreminder.ui.components.AiEditReview(proposal, state.busy, onApplyEdit, onDismissDraft) } }
        if (state.actions.isNotEmpty()) item {
            Text("修改记录", style = MaterialTheme.typography.titleLarge)
            state.actions.take(10).forEach { record ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(record.title); Text(java.time.Instant.ofEpochMilli(record.createdAtEpochMillis).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")), style = MaterialTheme.typography.bodySmall) }
                    if (record.state == "APPLIED") TextButton(enabled = !state.busy, onClick = { undoId = record.id }) { Text("撤销") } else Text("已撤销", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("仅记录已应用的所选修改；新增草稿仍使用导入前恢复材料。后续有手动修改时禁止覆盖撤销。", style = MaterialTheme.typography.bodySmall)
        }
        item {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BarChart, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("让数据帮你复盘", style = MaterialTheme.typography.titleMedium)
                    Text("近30天匿名汇总，不含任务和歌曲名", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(enabled = ready && !state.busy, onClick = { onPrepare(AiAction.STATISTICS, "") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("解读使用统计") }
        }
        if (state.hasKey) item { TextButton(enabled = !state.busy, onClick = { clearKey = true }) { Text("清除本机 API Key") } }
    }
    if (showConfig) AiConfigDialog(configDraft ?: state.config, (configDraft?.profileId ?: state.config.profileId) == state.config.profileId && state.hasKey, onSave = { config, key -> onSave(config, key); showConfig = false }, onDismiss = { showConfig = false })
    undoId?.let { id -> AlertDialog(onDismissRequest = { undoId = null }, title = { Text("撤销该批修改？") }, text = { Text("将恢复该批修改前的事件配置。有任何后续修改或删除时，整批拒绝撤销；历史响铃记录保留。") }, confirmButton = { TextButton(onClick = { undoId = null; onUndo(id) }) { Text("确认撤销") } }, dismissButton = { TextButton(onClick = { undoId = null }) { Text("取消") } }) }
    if (clearKey) AlertDialog(onDismissRequest = { clearKey = false }, title = { Text("清除本机 API Key？") }, text = { Text("只移除本机保存的密钥，不影响服务商账户或现有提醒。") },
        confirmButton = { TextButton(onClick = { onClear(); clearKey = false }) { Text("清除") } }, dismissButton = { TextButton(onClick = { clearKey = false }) { Text("取消") } })
    state.pendingRequest?.let { request ->
        AlertDialog(onDismissRequest = onDismissRequest, title = { Text("确认向 AI 服务发送？") }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("实际地址：${request.config.requestEndpoint}\n模型：${request.config.model}\n输出上限：${request.config.maxTokens} tokens；可能计费，不自动重试。")
                if (request.config.isDeepSeek) Text("DeepSeek 思考模式：${if (request.config.deepSeekThinking) "开启" else "关闭"}", style = MaterialTheme.typography.bodySmall)
                Text("密钥通过认证头发送，不显示。以下是本次发送的全文。", style = MaterialTheme.typography.bodySmall)
                Text("系统提示\n${request.system}", style = MaterialTheme.typography.bodySmall)
                Text("用户内容\n${request.input}", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(enabled = !state.busy, onClick = onSend) { Text("确认发送") } }, dismissButton = { TextButton(onClick = onDismissRequest) { Text("取消") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiConfigDialog(initial: AiConfig, hasKey: Boolean, onSave: (AiConfig, String) -> Unit, onDismiss: () -> Unit) {
    var profileName by remember { mutableStateOf(initial.profileName) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var endpoint by remember { mutableStateOf(initial.endpoint) }
    var model by remember { mutableStateOf(initial.model) }
    var timeout by remember { mutableStateOf(initial.timeoutSeconds.toString()) }
    var tokens by remember { mutableStateOf(initial.maxTokens.toString()) }
    var json by remember { mutableStateOf(initial.jsonMode) }
    var completion by remember { mutableStateOf(initial.completionLimitField) }
    var thinking by remember { mutableStateOf(initial.deepSeekThinking) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    // 密钥不使用 rememberSaveable，不回填明文；留空保留原密钥。
    var secret by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val effective = runCatching { AiConfig(endpoint = endpoint.trim()).requestEndpoint }.getOrNull()
    val isDeepSeek = runCatching { URI(endpoint.trim()).host.equals("api.deepseek.com", true) }.getOrDefault(false)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                TopAppBar(title = { Text("AI 连接") }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭配置") } })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("启用 AI 助手", style = MaterialTheme.typography.titleMedium)
                            Text("关闭不影响离线提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("DeepSeek 快速配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("填入官方地址与 deepseek-flash；切换接收地址须填写匹配密钥。", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { endpoint = "https://api.deepseek.com"; model = "deepseek-flash"; completion = false; json = false; thinking = false; enabled = true; error = null }) { Text("DeepSeek 预设") }
                        }
                    }
                    OutlinedTextField(profileName, { profileName = it.take(100) }, label = { Text("连接名称") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"; model = "qwen-plus"; enabled = true; json = false; completion = false; thinking = false }) { Text("百炼·北京") }
                        OutlinedButton(onClick = { endpoint = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions"; model = "qwen-plus"; enabled = true; json = false; completion = false; thinking = false }) { Text("百炼·新加坡") }
                    }
                    Text("Key、地域和模型须来自同一百炼控制台。也可粘贴该业务空间的专属完整地址；不自动切换地域或模型。", style = MaterialTheme.typography.bodySmall)
                    Text("服务配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(endpoint, { endpoint = it; error = null }, label = { Text("服务地址") }, placeholder = { Text("https://api.deepseek.com") },
                        supportingText = { Text("接受根地址、/v1 基础地址或完整 Chat Completions 地址") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                    if (effective != null && endpoint.isNotBlank()) Text("实际请求\n$effective", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(model, { model = it; error = null }, label = { Text("模型名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(secret, { secret = it; error = null }, label = { Text(if (hasKey) "新 API Key（留空保留）" else "API Key") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text(if (hasKey) "已有密钥加密保存，不显示原文" else "通过 Android Keystore 加密后保存在本机") })
                    HorizontalDivider()
                    TextButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Tune, null, Modifier.size(20.dp))
                        Text("高级参数", Modifier.weight(1f).padding(start = 8.dp))
                        Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    }
                    if (advanced) {
                        OutlinedTextField(timeout, { timeout = it }, label = { Text("超时 5～120 秒") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(tokens, { tokens = it }, label = { Text("输出上限 256～8192 tokens") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(json, { json = it }); Text("识别时启用 JSON 模式", style = MaterialTheme.typography.bodyMedium) }
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(completion, { completion = it }); Text("使用 max_completion_tokens\n不勾选时使用 max_tokens", style = MaterialTheme.typography.bodySmall) }
                        if (isDeepSeek) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Switch(thinking, { thinking = it }); Text("DeepSeek 思考模式", Modifier.padding(start = 12.dp)) }
                            Text("开启可能增加等待和输出 token 消耗，简单安排可关闭；只对官方 api.deepseek.com 发送该参数。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("不发送 temperature，不自动切换模型，不跟随重定向。不支持 Responses、图片、语音或所有供应商专有参数。", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Security, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("个人自用密钥只在本机加密保存，不进入普通导出。手机直连不等同服务端级密钥保护，请勿在公开 APK 中放共享密钥。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(8.dp))
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(modifier = Modifier.weight(2f), onClick = {
                        runCatching {
                            AiConfig(enabled, endpoint.trim(), model.trim(), timeout.toIntOrNull() ?: error("超时须填写整数"), tokens.toIntOrNull() ?: error("输出上限须填写整数"), json, completion, thinking, initial.profileId, profileName.trim()).also {
                                if (enabled) { it.validateForRequest(); require((hasKey && runCatching { com.muyex.randommusicreminder.ai.credentialRecipient(initial) == com.muyex.randommusicreminder.ai.credentialRecipient(it) }.getOrDefault(false)) || secret.isNotBlank()) { "请填写 API Key" } }
                                if (secret.isNotBlank()) com.muyex.randommusicreminder.ai.validateApiKey(secret.trim())
                            }
                        }.onSuccess { onSave(it, secret.trim()); secret = "" }.onFailure { error = it.message ?: "参数无效" }
                    }) { Text("保存配置") }
                }
            }
        }
    }
}
