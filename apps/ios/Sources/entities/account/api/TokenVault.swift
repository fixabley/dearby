import Foundation
import Security

protocol TokenStore: Sendable {
    func load() throws -> TokenPair?
    func save(_ tokens: TokenPair) throws
    func clear() throws
}

/// Keychain storage for the passkey sign-in tokens (contract "패스키 로그인"). A new service name: the email-code
/// session (`dearby.account.session`) and anything kept before the 2026-10-03 prototype are neither read nor removed.
struct TokenVault: TokenStore {
    var service = "dearby.account.tokens"
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: "current"]
    }
    func load() throws -> TokenPair? {
        var request = query
        request[kSecReturnData as String] = true
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw VaultError(status: status) }
        return try JSONDecoder().decode(TokenPair.self, from: data)
    }
    func save(_ tokens: TokenPair) throws {
        let values: [String: Any] = [kSecValueData as String: try JSONEncoder().encode(tokens),
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
