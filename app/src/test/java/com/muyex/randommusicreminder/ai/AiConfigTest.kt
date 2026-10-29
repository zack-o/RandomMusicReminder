package com.muyex.randommusicreminder.ai

import org.junit.Assert.*
import org.junit.Test

class AiConfigTest {
    @Test fun resolvesDeepSeekRootAndVersionedBase() {
        listOf("https://api.deepseek.com", "https://api.deepseek.com/").forEach {
            assertEquals("https://api.deepseek.com/chat/completions", AiConfig(endpoint = it).requestEndpoint)
        }
        assertEquals("https://example.test/v1/chat/completions", AiConfig(endpoint = "https://example.test/v1/").requestEndpoint)
    }
    @Test fun preservesCustomAndCompletePaths() {
        listOf("https://example.test/custom/generate", "https://api.deepseek.com/chat/completions", "https://example.test/v1/chat/completions").forEach {
            assertEquals(it, AiConfig(endpoint = it).requestEndpoint)
        }
    }
    @Test fun isDisabledAndHasNoProviderByDefault() { assertFalse(AiConfig().enabled); assertEquals("", AiConfig().endpoint) }
    @Test fun acceptsExplicitHttpsEndpointWithPortAndPath() { validateEndpoint("https://example.test:8443/v1/chat/completions") }
    @Test fun rejectsHttpCredentialsQueryFragmentAndWhitespace() {
        listOf("http://example.test/v1", "https://user:secret@example.test/v1", "https://example.test/v1?key=abc",
            "https://example.test/v1#fragment", "https://example.test/\n", "file:///v1", "", "https:///v1").forEach {
            assertThrows(Exception::class.java) { validateEndpoint(it) }
        }
    }
    @Test fun enabledRequestRequiresBothEndpointAndModel() {
        assertThrows(IllegalArgumentException::class.java) { AiConfig().validateForRequest() }
        assertThrows(IllegalArgumentException::class.java) { AiConfig(true, "https://example.test/v1", "").validateForRequest() }
        AiConfig(true, "https://example.test/v1/chat/completions", "user-model").validateForRequest()
    }
    @Test fun boundsTimeoutAndOutput() {
        assertThrows(IllegalArgumentException::class.java) { AiConfig(timeoutSeconds = 4) }
        assertThrows(IllegalArgumentException::class.java) { AiConfig(timeoutSeconds = 121) }
        assertThrows(IllegalArgumentException::class.java) { AiConfig(maxTokens = 8193) }
        assertThrows(IllegalArgumentException::class.java) { AiConfig(maxTokens = 255) }
    }
    @Test fun rejectsHeaderInjectionAndBlankKeys() {
        listOf("", "secret\r\nAuthorization:other", "a b", "x".repeat(4097)).forEach {
            assertThrows(IllegalArgumentException::class.java) { validateApiKey(it) }
        }
        validateApiKey("test-placeholder-not-a-real-key")
    }
}
