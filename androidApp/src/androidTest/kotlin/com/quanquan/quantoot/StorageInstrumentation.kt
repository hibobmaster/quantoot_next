package com.quanquan.quantoot

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.quanquan.quantoot.platform.AndroidSecureStorage
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import kotlinx.coroutines.runBlocking

class StorageInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val results = Bundle()
        try {
            runBlocking {
                val context =
                    object : ContextWrapper(targetContext) {
                        override fun getPackageName(): String =
                            "${super.getPackageName()}.verification"

                        override fun getSharedPreferences(name: String, mode: Int) =
                            super.getSharedPreferences("verification.$name", mode)
                    }
                val preferences =
                    context.getSharedPreferences("FlutterSecureStorage", Context.MODE_PRIVATE)
                val newPreferences =
                    context.getSharedPreferences("quantoot_secure_v1", Context.MODE_PRIVATE)
                newPreferences.edit().clear().commit()
                val keyName = "mastodon_auth_state_v3"
                val payload =
                    """{"sessions":[{"instance_host":"social.example","access_token":"test-token-only","account":{"id":"42","acct":"test"}}],"active_session_key":"social.example::42"}"""
                val pair =
                    KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
                        .apply {
                            initialize(
                                KeyGenParameterSpec.Builder(
                                        "${context.packageName}.FlutterSecureStoragePluginKeyOAEP",
                                        KeyProperties.PURPOSE_ENCRYPT or
                                            KeyProperties.PURPOSE_DECRYPT,
                                    )
                                    .setDigests(
                                        KeyProperties.DIGEST_SHA256,
                                        KeyProperties.DIGEST_SHA1,
                                    )
                                    .setEncryptionPaddings(
                                        KeyProperties.ENCRYPTION_PADDING_RSA_OAEP
                                    )
                                    .build()
                            )
                        }
                        .generateKeyPair()
                val aes = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
                val wrapper =
                    Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStoreBCWorkaround")
                wrapper.init(
                    Cipher.WRAP_MODE,
                    pair.public,
                    OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        MGF1ParameterSpec.SHA1,
                        PSource.PSpecified.DEFAULT,
                    ),
                )
                context
                    .getSharedPreferences("FlutterSecureKeyStorage", Context.MODE_PRIVATE)
                    .edit()
                    .putString(
                        "AESVGhpcyBpcyB0aGUga2V5IGZvciBhIHNlY3VyZSBzdG9yYWdlIEFFUyBLZXkK",
                        Base64.encodeToString(wrapper.wrap(aes), Base64.DEFAULT),
                    )
                    .commit()
                val encrypt = Cipher.getInstance("AES/GCM/NoPadding")
                encrypt.init(Cipher.ENCRYPT_MODE, aes)
                val encrypted = encrypt.iv + encrypt.doFinal(payload.toByteArray())
                val oldKey = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg_$keyName"
                preferences
                    .edit()
                    .putString(oldKey, Base64.encodeToString(encrypted, Base64.DEFAULT))
                    .commit()
                val storage = AndroidSecureStorage(context)
                check(storage.read(keyName) == payload) { "Flutter account migration failed" }
                check(!preferences.contains(oldKey)) {
                    "Migrated token remains in the previous store"
                }
                check(!newPreferences.getString(keyName, "")!!.contains("test-token-only")) {
                    "Token was written without encryption"
                }
                check(AndroidSecureStorage(context).read(keyName) == payload) {
                    "Migrated account did not survive reopening"
                }
                storage.write(keyName, "{\"sessions\":[]}")
                check(storage.read(keyName) == "{\"sessions\":[]}") {
                    "Account removal did not persist"
                }
                storage.write(keyName, null)
                check(storage.read(keyName) == null) { "Removed account reappeared" }
            }
            results.putString(
                "stream",
                "Keystore encryption, Flutter v3 import, reopen and removal passed.\n",
            )
            finish(Activity.RESULT_OK, results)
        } catch (failure: Throwable) {
            results.putString(
                "stream",
                "Storage verification failed: ${failure.javaClass.simpleName}: ${failure.message}\n",
            )
            finish(Activity.RESULT_CANCELED, results)
        }
    }
}
