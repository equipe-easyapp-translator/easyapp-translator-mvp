package com.easyapp.translator.translation

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda a chave de API do Claude do próprio usuário (FR-019/FR-020).
 *
 * A chave é cifrada com AES-GCM por uma chave gerada no Android Keystore, que
 * nunca sai do hardware; em disco ficam só o texto cifrado e o IV. Se o
 * Keystore perder a chave (restauração de backup, troca de aparelho), a leitura
 * falha e o app simplesmente volta a funcionar só com tradução local.
 */
class ClaudeKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val hasKey: Boolean get() = prefs.contains(CIPHERTEXT)

    /** Final da chave para mostrar na interface (ex.: "…a1B2"); a chave inteira nunca é exibida. */
    val hint: String? get() = prefs.getString(HINT, null)

    var consentAccepted: Boolean
        get() = prefs.getBoolean(CONSENT, false)
        set(value) = prefs.edit().putBoolean(CONSENT, value).apply()

    /** Liga/desliga o uso do Claude sem apagar a chave. */
    var enabled: Boolean
        get() = prefs.getBoolean(ENABLED, true)
        set(value) = prefs.edit().putBoolean(ENABLED, value).apply()

    /** Pronto para uso: chave salva, consentimento aceito e opção ligada. */
    val usable: Boolean get() = hasKey && consentAccepted && enabled

    fun save(apiKey: String) {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(HINT, "…" + trimmed.takeLast(4))
            .putBoolean(ENABLED, true)
            .apply()
    }

    /** Devolve a chave em texto puro só para montar o cliente da API; nunca registre em log. */
    fun load(): String? {
        val ciphertext = prefs.getString(CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        prefs.edit().remove(CIPHERTEXT).remove(IV).remove(HINT).remove(CONSENT).remove(ENABLED).apply()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        /** Nome do arquivo de SharedPreferences; excluído do backup em `res/xml/backup_rules.xml`. */
        const val PREFS = "claude_credential"
        private const val CIPHERTEXT = "ciphertext"
        private const val IV = "iv"
        private const val HINT = "hint"
        private const val CONSENT = "consent_accepted"
        private const val ENABLED = "enabled"
        private const val KEY_ALIAS = "easyapp_claude_api_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
