package com.muyex.randommusicreminder.ai

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class AiProfilesIntegrationTest {
    @Test fun encryptedKeysSurviveRestartAndNeverFollowRecipientOrProfileChanges() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "v3-fixture-${UUID.randomUUID()}-"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(prefix + name, mode)
        }
        try {
            val first = AiConfig(true,"https://example.invalid","fixture",profileName="测试一")
            val second = first.copy(profileId=UUID.randomUUID().toString(),profileName="测试二")
            val store=AiSettingsStore(context)
            store.save(first,"synthetic-key-one");store.save(second,"synthetic-key-two")
            assertEquals("synthetic-key-two",store.secret())
            val restored=AiSettingsStore(context);assertEquals(2,restored.profiles.value.size)
            restored.select("default");assertEquals("synthetic-key-one",restored.secret())
            val changed=first.copy(endpoint="https://other.invalid")
            assertFalse(restored.hasKey(changed))
            assertThrows(IllegalArgumentException::class.java) { restored.save(changed,"") }
            assertThrows(IllegalArgumentException::class.java) { restored.secret(changed) }
            assertTrue(restored.hasKey(first.copy(model="another-model")))
            restored.clearKey();assertFalse(restored.hasKey())
            restored.select(second.profileId);assertEquals("synthetic-key-two",restored.secret())
            val plaintext=context.getSharedPreferences("ai_credentials",Context.MODE_PRIVATE).all.values.joinToString()
            assertFalse(plaintext.contains("synthetic-key"))
        } finally { target.deleteSharedPreferences(prefix+"ai_config");target.deleteSharedPreferences(prefix+"ai_credentials") }
    }
}
