package com.vxhub.internal.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.vxhub.internal.VxLog
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets (the account's refresh token) before they reach disk. */
internal interface SecretCipher {
    fun encrypt(plain: String): String?
    fun decrypt(encoded: String): String?
}

/**
 * AES-256-GCM with a non-exportable key in the Android Keystore — what Jetpack Security's
 * deprecated `EncryptedSharedPreferences` did, without the dependency.
 *
 * The key never leaves the device and is deleted with the app, so a refresh token read off disk
 * (or restored from a backup) is useless anywhere else. Any failure returns null: losing a session
 * means signing in again, which beats crashing on a vendor's broken Keystore.
 */
internal class KeystoreSecretCipher(private val alias: String = "com.vxhub.secret.v1") : SecretCipher {

    override fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(sealed, Base64.NO_WRAP)
    } catch (e: Exception) {
        VxLog.e("Could not encrypt a secret; it will not be persisted", e)
        null
    }

    override fun decrypt(encoded: String): String? = try {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = sealed.copyOfRange(0, IV_LENGTH)
        val body = sealed.copyOfRange(IV_LENGTH, sealed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }
        String(cipher.doFinal(body), Charsets.UTF_8)
    } catch (e: Exception) {
        VxLog.w("A stored secret could not be decrypted and is discarded (${e.javaClass.simpleName})")
        null
    }

    @Synchronized
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
