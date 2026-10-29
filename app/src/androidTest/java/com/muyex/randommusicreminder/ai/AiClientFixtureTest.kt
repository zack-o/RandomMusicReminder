package com.muyex.randommusicreminder.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiClientFixtureTest {
    private class Connection(private val status: Int = 200, private val response: String) : HttpsURLConnection(URL("https://example.invalid/v1/chat/completions")) {
        val body = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "fixture-only"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getResponseCode() = status
        override fun getOutputStream() = body
        override fun getInputStream() = ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
        override fun getErrorStream() = ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
    }
    @Test fun encodesRequestAndParsesFixedResponseWithoutNetwork() = runBlocking {
        val connection = Connection(response = """{"choices":[{"finish_reason":"stop","message":{"content":"OK","refusal":null}}]}""")
        val client = AiClient { connection }
        val response = client.complete(AiConfig(true, "https://example.invalid/v1/chat/completions", "fixture", jsonMode = true), "placeholder-not-a-real-key", "只输出JSON", "测试")
        assertEquals("OK", response)
        val body = JSONObject(connection.body.toString("UTF-8"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.disconnected)
    }
    @Test fun truncationCannotBecomeApplicableDraft() = runBlocking {
        val connection = Connection(response = """{"choices":[{"finish_reason":"length","message":{"content":"{}"}}]}""")
        try {
            AiClient { connection }.complete(AiConfig(true, "https://example.invalid/v1/chat/completions", "fixture"), "placeholder", "JSON", "测试")
            fail("Truncated response must fail")
        } catch (_: IllegalArgumentException) { assertTrue(connection.disconnected) }
    }
    @Test fun deepSeekRootResolvesAndThinkingIsExplicitWithoutChangingModel() = runBlocking {
        val connection = Connection(response = """{"choices":[{"finish_reason":"stop","message":{"content":"OK"}}]}""")
        var actual = ""
        AiClient { actual = it; connection }.complete(AiConfig(true, "https://api.deepseek.com", "deepseek-flash"), "placeholder", "测试", "OK")
        assertEquals("https://api.deepseek.com/chat/completions", actual)
        val body = JSONObject(connection.body.toString("UTF-8"))
        assertEquals("deepseek-flash", body.getString("model"))
        assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
        assertTrue(body.has("max_tokens"))
    }
    @Test fun serviceErrorCannotExposeEchoedCredentials() = runBlocking {
        val connection = Connection(401, """{"error":{"message":"Bearer placeholder private task","code":"placeholder"}}""")
        try {
            AiClient { connection }.complete(AiConfig(true, "https://example.invalid", "fixture"), "placeholder", "测试", "OK")
            fail("401 must fail")
        } catch (error: AiFailure) {
            assertTrue(error.message.orEmpty().contains("认证失败"))
            assertFalse(error.message.orEmpty().contains("placeholder"))
            assertTrue(connection.disconnected)
        }
    }
    @Test fun redirectDoesNotReplayCredentials() = runBlocking {
        val connection = Connection(302, "not used")
        var opened = 0
        try {
            AiClient { opened++; connection }.complete(AiConfig(true, "https://example.invalid/v1/chat/completions", "fixture"), "placeholder", "JSON", "测试")
            fail("Redirect must fail")
        } catch (_: IllegalArgumentException) { assertEquals(1, opened); assertTrue(connection.disconnected) }
    }
}
