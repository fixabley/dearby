import Foundation
import Observation

/// Email-code sign-in, asked for only when publishing, sharing or saving a received card.
@MainActor @Observable final class AccountViewModel {
    enum Phase: Equatable { case signedOut, sendingCode, codeSent, verifying, signedIn }
    private(set) var phase: Phase
    private(set) var email = ""
    private(set) var message: String?
    private(set) var session: AccountSession?
    private var challengeID: String?
    let client: AccountClient
    private let vault: any SessionStore

    init(client: AccountClient, vault: any SessionStore) {
        self.client = client
        self.vault = vault
        let stored = try? vault.load()
        session = stored
        phase = stored == nil ? .signedOut : .signedIn
    }

    func requestCode(_ address: String) async {
        let address = address.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard address.contains("@"), address.count <= 254 else { message = "이메일 주소를 확인해 주세요."; return }
        phase = .sendingCode
        message = nil
        do {
            challengeID = try await client.requestCode(email: address)
            email = address
            phase = .codeSent
        } catch {
            phase = challengeID == nil ? .signedOut : .codeSent
            message = Self.text(error, fallback: "인증번호를 보내지 못했어요. 잠시 후 다시 시도해 주세요.")
        }
    }
    func verify(_ code: String) async {
        let code = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let challengeID, code.count == 6, code.allSatisfy(\.isASCII), code.allSatisfy(\.isNumber) else {
            message = "인증번호 6자리를 입력해 주세요."
            return
        }
        phase = .verifying
        message = nil
        do {
            let session = try await client.signIn(challengeId: challengeID, code: code)
            try vault.save(session)
            self.session = session
            self.challengeID = nil
            phase = .signedIn
        } catch AccountError.unauthorized {
            phase = .codeSent
            message = "인증번호가 맞지 않거나 만료됐어요."
        } catch {
            phase = .codeSent
            message = Self.text(error, fallback: "로그인하지 못했어요. 잠시 후 다시 시도해 주세요.")
        }
    }
    /// Back to the email step, e.g. to fix a mistyped address.
    func changeEmail() {
        challengeID = nil
        message = nil
        phase = .signedOut
    }
    /// Signing out always forgets the local session, even if the server call fails.
    func signOut() async {
        if let session { try? await client.signOut(session) }
        forget()
    }
    /// Runs an owner call; an expired or revoked session signs out instead of retrying.
    func authorized<Value: Sendable>(_ call: @Sendable (AccountSession) async throws -> Value) async throws -> Value {
        guard let session else { throw AccountError.unauthorized }
        do { return try await call(session) } catch AccountError.unauthorized {
            forget()
            message = "로그인이 만료됐어요. 다시 로그인해 주세요."
            throw AccountError.unauthorized
        }
    }
    private func forget() {
        try? vault.clear()
        session = nil
        challengeID = nil
        phase = .signedOut
    }
    private static func text(_ error: Error, fallback: String) -> String {
        switch error as? AccountError {
        case .rateLimited: "요청이 많아요. 잠시 후 다시 시도해 주세요."
        case .invalidInput: "이메일 주소를 확인해 주세요."
        default: fallback
        }
    }
}
