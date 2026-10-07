package dev.thorpilot

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials stay in this app's sandbox, encrypted with a non-exportable device key. */
class ConnectionStore(context: Context, preferencesName: String = "connection") {
    private val keyAlias = if (preferencesName == "connection") "romarr" else "romarr:$preferencesName"
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    val url: String get() = prefs.getString("url", "") ?: ""
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
    fun save(rawUrl: String, token: String) {
        val url = ServerAddress.normalize(rawUrl)
        require(token.isNotBlank()) { "Enter your server API key." }
        require(token.length <= 4096 && token.none { it.code < 32 || it.code == 127 }) { "Enter a valid server API key." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(prefs.edit().putString("url", url).putString("token", encrypted).commit())
    }
    fun clear() { check(prefs.edit().clear().commit()) { "Could not forget the connection. Try again." } }
}
