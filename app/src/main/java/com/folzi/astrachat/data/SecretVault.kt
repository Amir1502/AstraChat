package com.folzi.astrachat.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.folzi.astrachat.core.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString

@Singleton
class SecretVault @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("encrypted_credentials", Context.MODE_PRIVATE)
    private val alias = "astra.credentials.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    @Synchronized
    fun read(id: String): Credentials {
        val encoded = prefs.getString(id, null) ?: return Credentials()
        return try {
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            require(data.size >= 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            cipher.updateAAD(id.toByteArray())
            json.decodeFromString<Credentials>(cipher.doFinal(data.copyOfRange(12, data.size)).decodeToString())
        } catch (_: Exception) { throw SafeFailure(FailureKind.STORAGE) }
    }
    @Synchronized
    fun write(id: String, credentials: Credentials) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(id.toByteArray())
        val encrypted = cipher.doFinal(json.encodeToString(credentials).toByteArray())
        check(prefs.edit().putString(id, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).commit())
    }
    @Synchronized
    fun delete(id: String) { check(prefs.edit().remove(id).commit()) }
    fun exists(id: String): Boolean = prefs.contains(id)
}
