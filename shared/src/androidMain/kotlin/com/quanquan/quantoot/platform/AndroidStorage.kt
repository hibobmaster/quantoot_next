package com.quanquan.quantoot.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidPreferences(context: Context) : AppPreferences {
    private val preferences =
        context.getSharedPreferences("quantoot_preferences", Context.MODE_PRIVATE)
    private val flutterPreferences =
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    override suspend fun read(key: String): String? =
        withContext(Dispatchers.IO) {
            preferences.getString(key, null) ?: flutterPreferences.getString("flutter.$key", null)
        }

    override suspend fun write(key: String, value: String?) =
        withContext(Dispatchers.IO) {
            val editor = preferences.edit()
            if (value == null) editor.remove(key) else editor.putString(key, value)
            check(editor.commit()) { "Could not save app preferences" }
            if (value == null)
                check(flutterPreferences.edit().remove("flutter.$key").commit()) {
                    "Could not remove migrated preferences"
                }
        }

    override suspend fun removeByPrefix(prefix: String) = withContext(Dispatchers.IO) {
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        check(editor.commit()) { "Could not remove cached preferences" }
    }
}

class AndroidSecureStorage(context: Context) : KeyValueStore {
    private val preferences =
        context.getSharedPreferences("quantoot_secure_v1", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val alias = "com.quanquan.quantoot.sessions.v1"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val legacyPreferences =
        context.getSharedPreferences("FlutterSecureStorage", Context.MODE_PRIVATE)
    private val legacyKeys =
        context.getSharedPreferences("FlutterSecureKeyStorage", Context.MODE_PRIVATE)
    private val legacyRsaAlias = "${context.packageName}.FlutterSecureStoragePluginKeyOAEP"

    private fun key(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
            }
            .generateKey()
    }

    override suspend fun read(key: String): String? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val encoded =
                    preferences.getString(key, null) ?: return@withLock importFlutterRecord(key)
                val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
                require(encrypted.size > 28) { "Invalid encrypted account record" }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    this@AndroidSecureStorage.key(),
                    GCMParameterSpec(128, encrypted.copyOfRange(0, 12)),
                )
                cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
                cipher.doFinal(encrypted, 12, encrypted.size - 12).toString(Charsets.UTF_8)
            }
        }

    private fun importFlutterRecord(recordKey: String): String? {
        val legacyKey = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg_$recordKey"
        val encoded = legacyPreferences.getString(legacyKey, null) ?: return null
        require(encoded.length < 3 * 1024 * 1024) { "Saved account record exceeds the limit" }
        val wrapped =
            legacyKeys.getString(
                "AESVGhpcyBpcyB0aGUga2V5IGZvciBhIHNlY3VyZSBzdG9yYWdlIEFFUyBLZXkK",
                null,
            ) ?: error("The previous account encryption key is unavailable")
        val privateKey =
            keyStore.getKey(legacyRsaAlias, null)
                ?: error("The previous account encryption key is unavailable")
        val rsa = Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStoreBCWorkaround")
        rsa.init(
            Cipher.UNWRAP_MODE,
            privateKey,
            OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT,
            ),
        )
        val legacyAes = rsa.unwrap(Base64.decode(wrapped, Base64.DEFAULT), "AES", Cipher.SECRET_KEY)
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        require(bytes.size > 28) { "Invalid previous account record" }
        val decipher = Cipher.getInstance("AES/GCM/NoPadding")
        decipher.init(
            Cipher.DECRYPT_MODE,
            legacyAes,
            GCMParameterSpec(128, bytes.copyOfRange(0, 12)),
        )
        val value = decipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(recordKey.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(
            preferences
                .edit()
                .putString(recordKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .commit()
        ) {
            "Could not migrate encrypted accounts"
        }
        check(legacyPreferences.edit().remove(legacyKey).commit()) {
            "Could not remove migrated account record"
        }
        return value
    }

    override suspend fun write(key: String, value: String?) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val editor = preferences.edit()
                if (value == null) {
                    editor.remove(key)
                } else {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, this@AndroidSecureStorage.key())
                    cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
                    val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
                    editor.putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                }
                check(editor.commit()) { "Could not save encrypted account record" }
            }
        }
}
