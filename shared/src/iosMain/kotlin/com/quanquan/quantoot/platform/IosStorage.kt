@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.quanquan.quantoot.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Security.*

class IosPreferences : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun read(key: String): String? =
        defaults.stringForKey(key) ?: defaults.stringForKey("flutter.$key")

    override suspend fun write(key: String, value: String?) {
        if (value == null) {
            defaults.removeObjectForKey(key)
            defaults.removeObjectForKey("flutter.$key")
        } else defaults.setObject(value, forKey = key)
    }
}

class IosSecureStorage : KeyValueStore {
    private val service = "com.quanquan.quantoot.sessions"

    override suspend fun read(key: String): String? {
        val current = readItem(key, service)
        if (current != null) return current
        if (key != "mastodon_auth_state_v3") return null
        val legacy = readItem(key, "flutter_secure_storage_service") ?: return null
        write(key, legacy)
        withQuery(key, "flutter_secure_storage_service") { dictionary ->
            val status = SecItemDelete(dictionary)
            check(status == errSecSuccess || status == errSecItemNotFound) {
                "Could not remove migrated Keychain record ($status)"
            }
        }
        return legacy
    }

    private fun readItem(key: String, serviceName: String): String? = memScoped {
        withQuery(key, serviceName) { dictionary ->
            CFDictionaryAddValue(dictionary, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(dictionary, kSecMatchLimit, kSecMatchLimitOne)
            val result = alloc<CFTypeRefVar>()
            result.value = null
            val status = SecItemCopyMatching(dictionary, result.ptr)
            if (status == errSecItemNotFound) return@withQuery null
            check(status == errSecSuccess) { "Could not read Keychain record ($status)" }
            val data =
                CFBridgingRelease(result.value) as? NSData ?: error("Invalid Keychain record")
            NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
                ?: error("Invalid Keychain text")
        }
    }

    override suspend fun write(key: String, value: String?) {
        withQuery(key, service) { dictionary ->
            if (value == null) {
                val status = SecItemDelete(dictionary)
                check(status == errSecSuccess || status == errSecItemNotFound) {
                    "Could not remove Keychain record ($status)"
                }
            } else {
                val bytes = value.encodeToByteArray()
                val data =
                    if (bytes.isEmpty()) NSData()
                    else
                        bytes.usePinned {
                            NSData.create(bytes = it.addressOf(0), length = bytes.size.convert())
                        }
                val dataRef = CFBridgingRetain(data)!!
                try {
                    withDictionary { update ->
                        CFDictionaryAddValue(update, kSecValueData, dataRef)
                        val status = SecItemUpdate(dictionary, update)
                        if (status == errSecItemNotFound) {
                            CFDictionaryAddValue(dictionary, kSecValueData, dataRef)
                            CFDictionaryAddValue(
                                dictionary,
                                kSecAttrAccessible,
                                kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                            )
                            val added = SecItemAdd(dictionary, null)
                            check(added == errSecSuccess) {
                                "Could not save Keychain record ($added)"
                            }
                        } else
                            check(status == errSecSuccess) {
                                "Could not update Keychain record ($status)"
                            }
                    }
                } finally {
                    CFRelease(dataRef)
                }
            }
        }
    }

    private inline fun <T> withQuery(
        key: String,
        serviceName: String,
        block: (CFDictionaryRef) -> T,
    ): T {
        val account = CFBridgingRetain(key)!!
        val service = CFBridgingRetain(serviceName)!!
        return try {
            withDictionary { dictionary ->
                CFDictionaryAddValue(dictionary, kSecClass, kSecClassGenericPassword)
                CFDictionaryAddValue(dictionary, kSecAttrAccount, account)
                CFDictionaryAddValue(dictionary, kSecAttrService, service)
                CFDictionaryAddValue(dictionary, kSecAttrSynchronizable, kCFBooleanFalse)
                block(dictionary)
            }
        } finally {
            CFRelease(account)
            CFRelease(service)
        }
    }

    private inline fun <T> withDictionary(block: (CFDictionaryRef) -> T): T {
        val dictionary =
            CFDictionaryCreateMutable(null, 0, null, null)
                ?: error("Could not allocate Keychain query")
        return try {
            block(dictionary)
        } finally {
            CFRelease(dictionary)
        }
    }
}
