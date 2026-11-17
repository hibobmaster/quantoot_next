import XCTest
import Security
import QuanTootShared

@MainActor
final class StorageTests: XCTestCase {
    func testKeychainRoundTripAndRemoval() async throws {
        let key = "quantoot-verification-\(UUID().uuidString)"
        let storage = IosSecureStorage()
        try await storage.write(key: key, value: "test-only-account")
        let restored = try await IosSecureStorage().read(key: key)
        XCTAssertEqual(restored, "test-only-account")
        try await storage.write(key: key, value: "updated-test-only")
        let updated = try await storage.read(key: key)
        XCTAssertEqual(updated, "updated-test-only")
        try await storage.write(key: key, value: nil)
        let removed = try await storage.read(key: key)
        XCTAssertNil(removed)
    }

    func testFlutterKeychainMigration() async throws {
        let key = "mastodon_auth_state_v3"
        let current: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.quanquan.quantoot.sessions",
            kSecAttrAccount as String: key,
        ]
        var currentLookup = current
        currentLookup[kSecReturnData as String] = true
        var currentData: CFTypeRef?
        let currentStatus = SecItemCopyMatching(currentLookup as CFDictionary, &currentData)
        if currentStatus == errSecSuccess {
            guard currentData as? Data == Data("test-only-legacy-record".utf8) else {
                throw XCTSkip("Run storage migration tests in a dedicated simulator.")
            }
            XCTAssertEqual(SecItemDelete(current as CFDictionary), errSecSuccess)
        } else {
            XCTAssertEqual(currentStatus, errSecItemNotFound)
            guard currentStatus == errSecItemNotFound else { return }
        }
        let original: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "flutter_secure_storage_service",
            kSecAttrAccount as String: key,
        ]
        var lookup = original
        lookup[kSecReturnData as String] = true
        var previous: CFTypeRef?
        let existing = SecItemCopyMatching(lookup as CFDictionary, &previous)
        if existing == errSecSuccess {
            guard previous as? Data == Data("test-only-legacy-record".utf8) else {
                throw XCTSkip("Run storage migration tests in a dedicated simulator.")
            }
            XCTAssertEqual(SecItemDelete(original as CFDictionary), errSecSuccess)
        } else {
            XCTAssertEqual(existing, errSecItemNotFound)
            guard existing == errSecItemNotFound else { return }
        }
        var new = original
        new[kSecValueData as String] = Data("test-only-legacy-record".utf8)
        XCTAssertEqual(SecItemAdd(new as CFDictionary, nil), errSecSuccess)
        let storage = IosSecureStorage()
        let restored = try await storage.read(key: key)
        XCTAssertEqual(restored, "test-only-legacy-record")
        XCTAssertEqual(SecItemCopyMatching(original as CFDictionary, nil), errSecItemNotFound)
        try await storage.write(key: key, value: nil)
    }
}
