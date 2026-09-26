import Foundation
import Security

struct SessionModel: Codable, Sendable {
    var sessionToken: String
    var profileId: String
}
struct SessionVault {
    var namespace: String
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: "dearby.native.session.\(namespace)", kSecAttrAccount as String: "current"]
    }
    func load() throws -> SessionModel? {
        var request = query
        request[kSecReturnData as String] = true
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw VaultError(status: status) }
        return try JSONDecoder().decode(SessionModel.self, from: data)
    }
    func save(_ session: SessionModel) throws {
        let data = try JSONEncoder().encode(session)
        let values: [String: Any] = [kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
        let status = SecItemUpdate(query as CFDictionary, values as CFDictionary)
        if status == errSecItemNotFound {
            let result = SecItemAdd(query.merging(values) { _, new in new } as CFDictionary, nil)
            guard result == errSecSuccess else { throw VaultError(status: result) }
        } else if status != errSecSuccess { throw VaultError(status: status) }
    }
    func clear() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw VaultError(status: status) }
    }
    struct VaultError: LocalizedError {
        var status: OSStatus
        var errorDescription: String? { "보안 저장소에 접근할 수 없습니다. (\(status))" }
    }
}
