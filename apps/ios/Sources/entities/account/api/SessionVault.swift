import Foundation
import Security

protocol SessionStore: Sendable {
    func load() throws -> AccountSession?
    func save(_ session: AccountSession) throws
    func clear() throws
}

/// Keychain storage for the owner session. A new service name: sessions kept by the app
/// before the 2026-10-03 prototype are neither read nor removed.
struct SessionVault: SessionStore {
    var service = "dearby.account.session"
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: "current"]
    }
    func load() throws -> AccountSession? {
        var request = query
        request[kSecReturnData as String] = true
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw VaultError(status: status) }
        return try JSONDecoder().decode(AccountSession.self, from: data)
    }
    func save(_ session: AccountSession) throws {
        let values: [String: Any] = [kSecValueData as String: try JSONEncoder().encode(session),
                                     kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
        let status = SecItemUpdate(query as CFDictionary, values as CFDictionary)
        if status == errSecItemNotFound {
            let added = SecItemAdd(query.merging(values) { _, new in new } as CFDictionary, nil)
            guard added == errSecSuccess else { throw VaultError(status: added) }
        } else if status != errSecSuccess { throw VaultError(status: status) }
    }
    func clear() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw VaultError(status: status) }
    }
    struct VaultError: Error { let status: OSStatus }
}
