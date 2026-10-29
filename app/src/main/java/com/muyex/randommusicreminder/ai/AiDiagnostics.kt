package com.muyex.randommusicreminder.ai

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException
import javax.net.ssl.SSLException

class AiFailure(message: String) : IllegalArgumentException(message)

fun aiHttpFailure(status: Int, code: String? = null): String {
    val advice = when {
        code in setOf("model_not_found", "invalid_model", "ModelNotFound") -> "模型不可用，请核对模型名称和账户访问权限。"
        status in 300..399 -> "服务返回重定向，为保护密钥已阻止跳转；请填写最终 HTTPS 请求地址。"
        status == 400 || status == 422 -> "请求参数不兼容。请核对模型，尝试在高级参数中关闭 JSON 模式或改用 max_tokens。"
        status == 401 -> "认证失败，请检查密钥是否有效、是否属于当前服务商；可重新保存密钥。"
        status == 402 -> "账户余额或额度不足，请到服务商平台核对；修改本地提醒不会解决此问题。"
        status == 403 -> "服务拒绝访问，请核对账户权限、地区限制和网络出口。"
        status == 404 || status == 405 -> "请求地址不存在或不接受此方法。请检查实际地址应指向 Chat Completions，而不是官网或 Responses。"
        status == 408 || status == 504 -> "服务响应超时，可稍后手动重试或提高超时；DeepSeek 可关闭思考模式。"
        status == 413 -> "本次内容过长，请减少输入或分批识别。"
        status == 429 -> "请求限流或额度限制，请稍后手动重试并检查服务商配额。"
        status >= 500 -> "服务暂时异常或繁忙，请稍后手动重试。"
        else -> "服务未接受请求，请核对实际地址、模型及账户权限。"
    }
    // 只采用受控错误类别；原始错误正文可能回显认证信息或用户输入，不显示也不记录。
    return "HTTP $status · $advice 未自动重试。"
}

fun aiSafeFailure(error: Exception): Exception = when (error) {
    is AiFailure -> error
    is SocketTimeoutException -> AiFailure("连接或读取超时。请检查网络，或提高超时；DeepSeek 可关闭思考模式。未自动重试。")
    is UnknownHostException -> AiFailure("无法解析服务地址。请检查域名、手机联网和 DNS 设置。未自动重试。")
    is SSLException -> AiFailure("HTTPS 安全连接失败。请检查手机日期、证书或代理，不建议关闭证书校验。")
    is IOException -> AiFailure("网络连接中断。请检查手机网络及代理后手动重试。")
    else -> AiFailure("服务返回的内容无法解析为支持的 Chat Completions 文本响应；未应用任何数据。")
}
