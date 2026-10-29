package com.muyex.randommusicreminder.ai

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class AiDiagnosticsTest {
    @Test fun distinguishesActionableFailures() {
        assertTrue(aiHttpFailure(401).contains("认证失败"))
        assertTrue(aiHttpFailure(402).contains("余额"))
        assertTrue(aiHttpFailure(404).contains("Chat Completions"))
        assertTrue(aiHttpFailure(429).contains("限流"))
        assertTrue(aiHttpFailure(400, "model_not_found").contains("模型不可用"))
    }
    @Test fun doesNotEchoArbitraryServiceErrorsOrSecrets() {
        val text = "Bearer secret-placeholder echoed private task"
        assertFalse(aiHttpFailure(400, text).contains(text))
        assertFalse(aiSafeFailure(IllegalStateException(text)).message.orEmpty().contains(text))
        assertTrue(aiHttpFailure(302).contains("已阻止跳转"))
    }
    @Test fun explainsNetworkFailuresWithoutRawExceptionText() {
        assertTrue(aiSafeFailure(SocketTimeoutException("private")).message.orEmpty().contains("超时"))
        assertTrue(aiSafeFailure(UnknownHostException("private")).message.orEmpty().contains("域名"))
    }
}
