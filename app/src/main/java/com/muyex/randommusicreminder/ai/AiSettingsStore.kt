package com.muyex.randommusicreminder.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AiSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
    private val credentials = context.getSharedPreferences("ai_credentials", Context.MODE_PRIVATE)
    private fun legacy() = runCatching {
        AiConfig(preferences.getBoolean("enabled", false), preferences.getString("endpoint", "").orEmpty(), preferences.getString("model", "").orEmpty(),
            preferences.getInt("timeout", 30), preferences.getInt("tokens", 2048), preferences.getBoolean("json", false), preferences.getBoolean("completionLimit", false),
            preferences.getBoolean("deepSeekThinking", false))
    }.getOrDefault(AiConfig())
    private val loaded = runCatching {
        val array = JSONArray(preferences.getString("profiles", "[]"))
        (0 until array.length()).map { index -> array.getJSONObject(index).let { item ->
            AiConfig(item.getBoolean("enabled"), item.getString("endpoint"), item.getString("model"), item.getInt("timeout"), item.getInt("tokens"),
                item.getBoolean("json"), item.getBoolean("completion"), item.getBoolean("thinking"), item.getString("id"), item.getString("name"))
        } }.also { list -> require(list.size <= 20 && list.map { it.profileId }.distinct().size == list.size) }
    }.getOrDefault(emptyList()).ifEmpty { listOf(legacy()) }
    private val mutableProfiles = MutableStateFlow(loaded)
    val profiles = mutableProfiles.asStateFlow()
    private val mutableConfig = MutableStateFlow(loaded.firstOrNull { it.profileId == preferences.getString("active", "default") } ?: loaded.first())
    val config = mutableConfig.asStateFlow()
    init {
        // 把升级前的单一密钥绑定到它原来的接收地址，后续改地址不能隐式转移。
        if (credentials.contains("ciphertext") && !credentials.contains("recipient") && legacy().endpoint.isNotBlank()) {
            check(credentials.edit().putString("recipient", credentialRecipient(legacy())).commit())
        }
    }
    private fun prefix(value: AiConfig) = if (value.profileId == "default") "" else "${value.profileId}:"
    fun hasKey(value: AiConfig = config.value): Boolean = value.endpoint.isNotBlank() && credentials.contains(prefix(value) + "ciphertext") &&
        credentials.getString(prefix(value) + "recipient", null) == credentialRecipient(value)

    @Synchronized
    fun select(profileId: String) {
        val value = profiles.value.firstOrNull { it.profileId == profileId } ?: error("连接已不存在")
        check(preferences.edit().putString("active", profileId).commit()) { "切换连接失败" }
        mutableConfig.value = value
    }

    @Synchronized
    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun secret(value: AiConfig = config.value): String {
        require(hasKey(value)) { "此接收地址没有匹配的 API Key，请重新填写" }
        val keyPrefix = prefix(value)
        val encrypted = credentials.getString(keyPrefix + "ciphertext", null) ?: error("请在应用内填写 API Key")
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, Base64.decode(credentials.getString(keyPrefix + "iv", ""), Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8).also(::validateApiKey)
        } catch (_: Exception) { error("API Key 无法解密，请重新填写；不会回退到明文") }
    }

    @Synchronized
    fun save(value: AiConfig, replacementKey: String) {
        if (value.enabled) value.validateForRequest()
        require(profiles.value.any { it.profileId == value.profileId } || profiles.value.size < 20) { "最多保存20个连接" }
        if (value.enabled) require(replacementKey.isNotBlank() || hasKey(value)) { "新连接或接收地址变化后须填写匹配的 API Key" }
        if (replacementKey.isNotBlank()) {
            validateApiKey(replacementKey)
            require(value.endpoint.isNotBlank()) { "先填写接收地址" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, encryptionKey()) }
            val encrypted = cipher.doFinal(replacementKey.toByteArray(Charsets.UTF_8))
            val keyPrefix = prefix(value)
            check(credentials.edit().putString(keyPrefix + "iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(keyPrefix + "ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(keyPrefix + "recipient", credentialRecipient(value)).commit()) { "API Key 保存失败" }
        }
        val all = profiles.value.filterNot { it.profileId == value.profileId } + value
        val array = JSONArray().apply { all.forEach { item -> put(JSONObject().put("id", item.profileId).put("name", item.profileName)
            .put("enabled", item.enabled).put("endpoint", item.endpoint).put("model", item.model).put("timeout", item.timeoutSeconds)
            .put("tokens", item.maxTokens).put("json", item.jsonMode).put("completion", item.completionLimitField).put("thinking", item.deepSeekThinking)) } }
        check(preferences.edit().putString("profiles", array.toString()).putString("active", value.profileId)
            .putBoolean("enabled", value.enabled).putString("endpoint", value.endpoint).putString("model", value.model)
            .putInt("timeout", value.timeoutSeconds).putInt("tokens", value.maxTokens).putBoolean("json", value.jsonMode)
            .putBoolean("completionLimit", value.completionLimitField).putBoolean("deepSeekThinking", value.deepSeekThinking).commit()) { "AI 配置保存失败" }
        mutableProfiles.value = all
        mutableConfig.value = value
    }

    fun clearKey() {
        val keyPrefix = prefix(config.value)
        check(credentials.edit().remove(keyPrefix + "ciphertext").remove(keyPrefix + "iv").remove(keyPrefix + "recipient").commit()) { "清除密钥失败" }
    }
    private companion object { const val ALIAS = "lexing.ai.credentials.v1" }
}
