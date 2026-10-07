package dev.thorpilot

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.util.UUID
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials stay in this app's sandbox, encrypted with a non-exportable device key. */
class ConnectionStore(context: Context, preferencesName: String = "connection") {
    private companion object { val lock = Any() }
    private val keyAlias = if (preferencesName == "connection") "romarr" else "romarr:$preferencesName"
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    val url: String get() = prefs.getString("url", "") ?: ""
    val identity: String get() = synchronized(lock) {
        if (url.isBlank()) return@synchronized ""
        prefs.getString("identity", null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("identity", it).commit()) { "Could not bind the saved connection." }
        }
    }
    fun snapshot(): ConnectionSnapshot = synchronized(lock) { ConnectionSnapshot(identity, url, token()) }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun token(): String {
        val value = prefs.getString("token", null) ?: return ""
        val parts = value.split(":")
        check(parts.size == 2) { "Saved key is unavailable. Save your connection again." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }
    /** A blank key may reuse credentials only for this exact canonical server. */
    fun save(rawUrl: String, suppliedToken: String): Boolean = synchronized(lock) {
        val normalized = ServerAddress.normalize(rawUrl)
        val previousUrl = url
        if (suppliedToken.isBlank()) require(previousUrl.isNotBlank() && normalized == previousUrl) {
            "Enter an API key for the new server. Saved keys stay with their original server."
        }
        val previousToken = runCatching { token() }.getOrNull()
        val value = if (suppliedToken.isBlank()) previousToken.orEmpty() else suppliedToken
        require(value.isNotBlank()) { "Enter your server API key." }
        require(value.length <= 4096 && value.none { it.code < 32 || it.code == 127 }) { "Enter a valid server API key." }
        val changed = normalized != previousUrl || value != previousToken
        val nextIdentity = if (changed) UUID.randomUUID().toString() else identity
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(prefs.edit().putString("url", normalized).putString("token", encrypted).putString("identity", nextIdentity).commit())
        changed
    }
    fun clear() = synchronized(lock) {
        check(prefs.edit().clear().commit()) { "Could not forget the connection. Try again." }
    }
}

/** Deliberately not a data class: logs must not stringify the API key. */
class ConnectionSnapshot(val identity: String, val url: String, val token: String)
