package com.atmosferast.orbita.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** What is kept of a session: the refresh token and the email shown in Ajustes. */
data class StoredSession(val email: String, val refreshToken: String)

/**
 * Where the session lives between uses of the app. The access token only lives in memory
 * (15 minutes); the refresh token survives restarts, encrypted.
 */
interface TokenStore {
    var accessToken: String?
    fun load(): StoredSession?
    fun save(session: StoredSession)
    fun clear()
}

/**
 * The refresh token and the email, encrypted with an AES key that never leaves the Android
 * Keystore. Backups are off (allowBackup="false"), so the session never leaves the phone.
 */
@Singleton
class KeystoreTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : TokenStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile
    override var accessToken: String? = null

    override fun load(): StoredSession? {
        val email = prefs.getString(KEY_EMAIL, null)?.let(::decrypt)
        val refresh = prefs.getString(KEY_REFRESH, null)?.let(::decrypt)
        return if (email != null && refresh != null) StoredSession(email, refresh) else null
    }

    override fun save(session: StoredSession) {
        prefs.edit {
            putString(KEY_EMAIL, encrypt(session.email))
            putString(KEY_REFRESH, encrypt(session.refreshToken))
        }
    }

    override fun clear() {
        accessToken = null
        prefs.edit { clear() }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    // Unreadable (e.g. the key was lost): as if there was no session.
    private fun decrypt(stored: String): String? = runCatching {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_SIZE))
        String(cipher.doFinal(sealed, IV_SIZE, sealed.size - IV_SIZE), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val PREFS = "orbita_session"
        const val KEY_EMAIL = "email"
        const val KEY_REFRESH = "refresh_token"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "orbita_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
