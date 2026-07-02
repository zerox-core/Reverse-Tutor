package com.reversetutor.core.data.llm

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretStore {
    suspend fun put(ref: String, secret: String)
    suspend fun get(ref: String): String?
    suspend fun delete(ref: String)
}

class AndroidKeystoreSecretStore(
    context: Context,
    private val keyAlias: String = "reverse_tutor_llm_profile_key"
) : SecretStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "reverse_tutor_llm_profile_secrets",
        Context.MODE_PRIVATE
    )

    override suspend fun put(ref: String, secret: String) {
        val encoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            encryptWithKeystore(secret)
        } else {
            "legacy:${secret.toByteArray(Charsets.UTF_8).base64()}"
        }
        preferences.edit().putString(secretKey(ref), encoded).apply()
    }

    override suspend fun get(ref: String): String? {
        val encoded = preferences.getString(secretKey(ref), null) ?: return null
        return if (encoded.startsWith("legacy:")) {
            String(encoded.removePrefix("legacy:").base64Decode(), Charsets.UTF_8)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            decryptWithKeystore(encoded)
        } else {
            null
        }
    }

    override suspend fun delete(ref: String) {
        preferences.edit().remove(secretKey(ref)).apply()
    }

    private fun secretKey(ref: String): String = "secret.$ref"

    @TargetApi(Build.VERSION_CODES.M)
    private fun encryptWithKeystore(secret: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        return "v1:${cipher.iv.base64()}:${ciphertext.base64()}"
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun decryptWithKeystore(encoded: String): String? {
        val parts = encoded.split(":")
        if (parts.size != 3 || parts[0] != "v1") return null

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(128, parts[1].base64Decode())
        )
        return String(cipher.doFinal(parts[2].base64Decode()), Charsets.UTF_8)
    }

    @TargetApi(Build.VERSION_CODES.M)
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
        val entry = keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }
}

class InMemorySecretStore : SecretStore {
    private val values = linkedMapOf<String, String>()

    override suspend fun put(ref: String, secret: String) {
        values[ref] = secret
    }

    override suspend fun get(ref: String): String? = values[ref]

    override suspend fun delete(ref: String) {
        values.remove(ref)
    }
}

private fun ByteArray.base64(): String =
    Base64.encodeToString(this, Base64.NO_WRAP)

private fun String.base64Decode(): ByteArray =
    Base64.decode(this, Base64.NO_WRAP)
