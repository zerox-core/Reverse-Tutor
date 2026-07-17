package com.reversetutor.core.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidOnlineAuthStateStore(
    context: Context,
    private val keyAlias: String = keyAliasName
) : OnlineAuthStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    override suspend fun read(): OnlineAuthState? = withContext(Dispatchers.IO) {
        val encrypted = preferences.getString(statePreferenceKey, null)
            ?: return@withContext null
        OnlineAuthStateCodec.decode(decrypt(encrypted))
    }

    override suspend fun write(state: OnlineAuthState) = withContext(Dispatchers.IO) {
        val encrypted = encrypt(OnlineAuthStateCodec.encode(state))
        check(preferences.edit().putString(statePreferenceKey, encrypted).commit()) {
            "Could not persist online authentication state"
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        check(preferences.edit().remove(statePreferenceKey).commit()) {
            "Could not clear online authentication state"
        }
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "v1:${cipher.iv.base64()}:${ciphertext.base64()}"
    }

    private fun decrypt(encoded: String): String {
        val parts = encoded.split(":")
        require(parts.size == 3 && parts[0] == "v1") {
            "Unsupported online authentication state"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(128, parts[1].base64Decode())
        )
        return String(cipher.doFinal(parts[2].base64Decode()), Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val keySpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(keySpec)
            keyGenerator.generateKey()
        }
        return (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    companion object {
        const val preferencesName = "reverse_tutor_online_auth_credentials"
        const val keyAliasName = "reverse_tutor_online_auth_key"
        private const val statePreferenceKey = "auth.state"
    }
}

private fun ByteArray.base64(): String =
    Base64.encodeToString(this, Base64.NO_WRAP)

private fun String.base64Decode(): ByteArray =
    Base64.decode(this, Base64.NO_WRAP)
