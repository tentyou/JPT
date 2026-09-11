package com.example.onlinepull

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A single credential, encrypted with a non-exportable device key and excluded from backup. */
interface McpCredentials {
    fun read(): String?
    fun save(token: String)
    fun clear()
}

class McpTokenStore(context: Context, private val keyProvider: (() -> SecretKey)? = null) : McpCredentials {
    private val file = File(context.noBackupFilesDir, "valuation-mcp-token")

    override fun read(): String? {
        if (!file.exists()) return null
        try {
            val saved = JSONObject(file.inputStream().bufferedReader().use { it.readText() })
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(saved.getString("iv"), Base64.NO_WRAP)))
            return String(cipher.doFinal(Base64.decode(saved.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            throw McpFailure("无法读取已保存的凭据，请重新填写 Token；本地盘点资料不受影响")
        }
    }

    override fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val saved = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
        val pending = File.createTempFile(".valuation-mcp-token-", ".tmp", file.parentFile)
        try {
            FileOutputStream(pending).use { stream ->
                stream.write(saved.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            // Unlike AtomicFile.finishWrite, failed replacement must be observable by the caller.
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            throw McpFailure("凭据保存失败，原连接未替换")
        } finally {
            pending.delete()
        }
    }

    override fun clear() { if (file.exists() && !file.delete()) throw McpFailure("移除凭据失败，请重试") }

    private fun key(): SecretKey {
        keyProvider?.let { return it() }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private companion object { const val KEY_ALIAS = "valuation-mcp-token-v1" }
}

object McpCredentialInput {
    const val ENDPOINT = "https://mcp.zhrdc.net/valuation-mcp"

    fun parse(input: String): String {
        val text = input.replace("\\_", "_").trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val credential = if (text.startsWith("{")) {
            val config = try {
                JSONObject(text).getJSONObject("mcpServers").getJSONObject("valuation-mcp")
            } catch (_: Exception) { throw McpFailure("配置格式不正确，请粘贴 valuation-mcp 配置或单独的 Token") }
            val suppliedUrl = config.optString("url").trim()
            val url = Regex("^\\[[^]]*]\\(([^)]+)\\)$").matchEntire(suppliedUrl)?.groupValues?.get(1) ?: suppliedUrl
            if (url != ENDPOINT) throw McpFailure("此版本仅支持公司的 valuation-mcp 地址")
            config.optJSONObject("headers")?.optString("Authorization").orEmpty()
        } else text
        val token = credential.replace("\\_", "_").trim().replace(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "")
        if (token.length !in 20..4096 || token.any { it.isWhitespace() || it.code !in 33..126 }) {
            throw McpFailure("Token 格式不正确，请完整复制凭据")
        }
        return token
    }
}
