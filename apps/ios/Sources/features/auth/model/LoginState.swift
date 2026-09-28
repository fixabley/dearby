import Foundation

struct LoginState {
    let api: APIClient
    struct Challenge: Decodable, Sendable { let challengeId: String; let expiresAt: String }
    func challenge(email: String) async throws -> Challenge {
        struct Request: Encodable { let email: String }
        return try await api.request("POST", "auth/challenges", body: JSONEncoder().encode(Request(email: email)))
    }
}
