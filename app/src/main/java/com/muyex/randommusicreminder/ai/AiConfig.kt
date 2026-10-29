package com.muyex.randommusicreminder.ai

import java.net.URI

data class AiConfig(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val model: String = "",
    val timeoutSeconds: Int = 30,
    val maxTokens: Int = 2048,
    val jsonMode: Boolean = false,
    val completionLimitField: Boolean = false,
    val deepSeekThinking: Boolean = false,
    val profileId: String = "default",
    val profileName: String = "我的连接",
) {
    // 基础地址与原始 HTTP 请求地址不同；只补全明确的根路径和 /v1，不猜测自定义网关路径。
    val requestEndpoint: String get() {
        if (endpoint.isBlank()) return endpoint
        validateEndpoint(endpoint)
        val base = endpoint.trimEnd('/')
        return if (URI(base).path.orEmpty() in setOf("", "/v1")) "$base/chat/completions" else endpoint
    }
    val isDeepSeek: Boolean get() = endpoint.isNotBlank() && URI(endpoint).host.equals("api.deepseek.com", true)

    init {
        require(Regex("[A-Za-z0-9-]{1,64}").matches(profileId) && profileName.isNotBlank() && profileName.length <= 100) { "连接名称或标识无效" }
        require(timeoutSeconds in 5..120 && maxTokens in 256..8192) { "超时须为 5～120 秒，输出上限须为 256～8192" }
        require(model.length <= 200 && model.none { it.isISOControl() }) { "模型名称无效" }
        if (endpoint.isNotBlank()) validateEndpoint(endpoint)
    }

    fun validateForRequest() {
        require(enabled) { "请先启用并保存 AI 连接" }
        validateEndpoint(endpoint)
        require(model.isNotBlank()) { "请填写模型名称" }
    }
}

/** 密钥绑定到实际接收地址；改路径或服务商后不能沿用旧认证信息。 */
fun credentialRecipient(config: AiConfig): String {
    val uri = URI(config.requestEndpoint)
    return "${uri.scheme.lowercase()}://${uri.host.lowercase()}:${if (uri.port == -1) 443 else uri.port}${uri.rawPath}"
}

fun validateEndpoint(value: String) {
    require(value.length in 1..2048 && value.none { it.isWhitespace() || it.isISOControl() }) { "接口地址无效" }
    val uri = runCatching { URI(value) }.getOrNull() ?: throw IllegalArgumentException("接口地址格式无效")
    require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "须填写不含账号、查询参数和片段的 HTTPS 接口地址"
    }
}

fun validateApiKey(key: String) {
    require(key.isNotBlank() && key.length <= 4096 && key.none { it.isISOControl() || it.isWhitespace() }) { "API Key 为空或包含无效字符" }
}
