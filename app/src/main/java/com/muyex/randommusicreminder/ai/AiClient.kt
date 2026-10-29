package com.muyex.randommusicreminder.ai

import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONArray
import org.json.JSONObject

class AiClient(private val connectionFactory: (String) -> HttpsURLConnection = { URL(it).openConnection() as HttpsURLConnection }) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun complete(config: AiConfig, secret: String, system: String, input: String, forceText: Boolean = false): String {
        config.validateForRequest(); validateApiKey(secret)
        require(input.length in 1..24_000 && system.length <= 12_000) { "输入内容为空或过长" }
        return suspendCancellableCoroutine { continuation ->
            val connection = AtomicReference<HttpsURLConnection?>()
            fun disconnect() { runCatching { connection.getAndSet(null)?.disconnect() } }
            val job = scope.launch {
                try {
                    val request = JSONObject().put("model", config.model).put("stream", false)
                        .put(if (config.completionLimitField) "max_completion_tokens" else "max_tokens", config.maxTokens)
                        .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system))
                            .put(JSONObject().put("role", "user").put("content", input)))
                    if (config.jsonMode && !forceText) request.put("response_format", JSONObject().put("type", "json_object"))
                    if (config.isDeepSeek) request.put("thinking", JSONObject().put("type", if (config.deepSeekThinking) "enabled" else "disabled"))
                    val conn = connectionFactory(config.requestEndpoint)
                    connection.set(conn)
                    ensureActive()
                    conn.requestMethod = "POST"; conn.instanceFollowRedirects = false
                    conn.connectTimeout = config.timeoutSeconds * 1000; conn.readTimeout = config.timeoutSeconds * 1000
                    conn.doOutput = true
                    conn.setRequestProperty("Authorization", "Bearer $secret")
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                    val status = conn.responseCode
                    if (status !in 200..299) {
                        val code = runCatching {
                            conn.errorStream?.use { stream ->
                                val output = java.io.ByteArrayOutputStream(); val bytes = ByteArray(4096)
                                while (output.size() < 65_536) {
                                    ensureActive(); val count = stream.read(bytes, 0, minOf(bytes.size, 65_536 - output.size()))
                                    if (count < 0) break
                                    output.write(bytes, 0, count)
                                }
                                JSONObject(output.toString("UTF-8")).optJSONObject("error")?.optString("code")
                            }
                        }.getOrNull()
                        throw AiFailure(aiHttpFailure(status, code))
                    }
                    val text = conn.inputStream.use { stream ->
                        val output = java.io.ByteArrayOutputStream(); val bytes = ByteArray(8192)
                        while (true) {
                            ensureActive(); val count = stream.read(bytes); if (count < 0) break
                            if (output.size() + count > 1_048_576) throw AiFailure("AI 返回数据超过 1 MiB，已停止读取，未应用数据")
                            output.write(bytes, 0, count)
                        }; output.toString("UTF-8")
                    }
                    val choice = JSONObject(text).getJSONArray("choices").getJSONObject(0)
                    if (choice.optString("finish_reason") != "stop") throw AiFailure(
                        if (choice.optString("finish_reason") == "length") "模型输出已截断。请提高输出上限、缩短输入或关闭思考模式；未生成可应用草稿。"
                        else "模型未正常结束（拒绝、工具调用或服务中断），未生成可应用草稿。")
                    val message = choice.getJSONObject("message")
                    if (!message.isNull("refusal")) throw AiFailure("模型拒绝了本次请求，未应用任何数据")
                    val content = message.get("content")
                    if (content !is String || content.isBlank() || content.length > 100_000) throw AiFailure("服务未返回可用文本，或输出过长；未应用任何数据")
                    disconnect()
                    if (continuation.isActive) continuation.resume(content)
                } catch (error: Exception) {
                    disconnect()
                    if (continuation.isActive) continuation.resumeWithException(aiSafeFailure(error))
                } finally { disconnect() }
            }
            // 取消页面任务时同时断开连接，避免只隐藏加载状态而继续请求。
            continuation.invokeOnCancellation {
                scope.launch { disconnect() }
                job.cancel()
            }
        }
    }
}
