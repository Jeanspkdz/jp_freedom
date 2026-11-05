package com.jp.freedom.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SyncConfiguration(val endpoint: String, val hasWriteToken: Boolean)

sealed interface EndpointValidation {
    data class Valid(val endpoint: String) : EndpointValidation
    data class Invalid(val explanation: String) : EndpointValidation
}

object SyncEndpoint {
    fun validate(value: String): EndpointValidation {
        val raw = value.trim().trimEnd('/')
        val uri = runCatching { URI(raw) }.getOrNull()
            ?: return EndpointValidation.Invalid("Escribe una dirección HTTPS válida para la API.")
        val host = uri.host?.lowercase()
        val scheme = uri.scheme?.lowercase()
        if (host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) {
            return EndpointValidation.Invalid("La dirección debe incluir un servidor y no puede tener credenciales ni parámetros.")
        }
        if (scheme == null) return EndpointValidation.Invalid("La dirección debe empezar con HTTPS.")
        if (uri.path.isNotEmpty() && uri.path != "/v1/snapshot") {
            return EndpointValidation.Invalid("Usa la dirección base de la API o termina en /v1/snapshot.")
        }
        val localHttp = scheme == "http" &&
            (host == "localhost" || host == "127.0.0.1")
        if (scheme != "https" && !localHttp) {
            return EndpointValidation.Invalid("Por seguridad, la dirección debe usar HTTPS.")
        }
        val endpointPath = if (uri.path == "/v1/snapshot") "" else "/v1/snapshot"
        return EndpointValidation.Valid("$scheme://${uri.rawAuthority}${uri.path}$endpointPath")
    }
}

class SyncConfigurationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun configuration(): SyncConfiguration = SyncConfiguration(
        endpoint = preferences.getString(KEY_ENDPOINT, "").orEmpty(),
        hasWriteToken = preferences.getString(KEY_ENCRYPTED_TOKEN, null) != null,
    )

    fun saveEndpoint(endpoint: String) {
        check(preferences.edit().putString(KEY_ENDPOINT, endpoint).commit()) { "No se pudo guardar la dirección de sincronización." }
    }

    fun saveWriteToken(token: String) {
        val encrypted = encrypt(token)
        check(preferences.edit().putString(KEY_ENCRYPTED_TOKEN, encrypted).commit()) { "No se pudo guardar la clave de sincronización." }
    }

    fun writeToken(): String? = preferences.getString(KEY_ENCRYPTED_TOKEN, null)?.let(::decrypt)

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return listOf(
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(encrypted, Base64.NO_WRAP),
        ).joinToString(".")
    }

    private fun decrypt(value: String): String {
        val parts = value.split('.', limit = 2)
        check(parts.size == 2) { "La clave guardada no es válida. Vuelve a ingresarla." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
    }

    companion object {
        private const val PREFERENCES = "sync_configuration"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_ENCRYPTED_TOKEN = "encrypted_write_token"
        private const val KEY_ALIAS = "jp_freedom_sync_write_token"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
