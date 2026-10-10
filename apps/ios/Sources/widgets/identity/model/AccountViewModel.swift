import AuthenticationServices
import Observation
import SwiftUI

/// Passkey sign-in (contract "패스키 로그인"), asked for only when publishing, sharing or saving a received card.
@MainActor @Observable final class AccountViewModel {
    enum Phase: Equatable { case signedOut, signingIn, signedIn }
    private(set) var phase: Phase
    private(set) var message: String?
    private(set) var session: TokenPair?
    let client: AccountClient
    private let vault: any TokenStore
    /// One refresh at a time: refresh tokens rotate, so a second refresh with the same token would fail.
    private var refreshing: Task<TokenPair, Error>?

    init(client: AccountClient, vault: any TokenStore) {
        self.client = client
        self.vault = vault
        let stored = try? vault.load()
        session = stored
        phase = stored == nil ? .signedOut : .signedIn
    }

    /// "패스키로 로그인": choose one of this app's passkeys on the device (or a nearby device).
    func signIn(using controller: AuthorizationController) async {
        await start(failure: "패스키로 로그인하지 못했어요. 다시 시도하거나 새 패스키로 시작해 주세요.") { [client] in
            let challenge = try await client.authenticationOptions()
            let credential = try await Passkey.assert(challenge.options, with: controller)
            return try await client.authenticate(challengeId: challenge.challengeId, credential: credential)
        }
    }
    /// "새 패스키로 시작": makes a new passkey, and with it a new account.
    func register(using controller: AuthorizationController) async {
        await start(failure: "새 패스키를 만들지 못했어요. 잠시 후 다시 시도해 주세요.") { [client] in
            let challenge = try await client.registrationOptions()
            let credential = try await Passkey.register(challenge.options, with: controller)
            return try await client.register(challengeId: challenge.challengeId, credential: credential)
        }
    }
    /// Signing out always forgets the device tokens, even if the server call fails.
    func signOut() async {
        if let session { try? await client.logout(session) }
        forget()
    }
    /// Runs an owner call. A 401 refreshes the tokens once and retries; if that is refused, the tokens are
    /// forgotten and the screens fall back to signing in.
    func authorized<Value: Sendable>(_ call: @Sendable (TokenPair) async throws -> Value) async throws -> Value {
        guard let session else { throw AccountError.unauthorized }
        do { return try await call(session) } catch AccountError.unauthorized {}
        do { return try await call(try await renewed(after: session)) } catch AccountError.unauthorized {
            expire()
            throw AccountError.unauthorized
        }
    }

    private func start(failure: String, _ ceremony: () async throws -> TokenPair) async {
        phase = .signingIn
        message = nil
        do {
            let tokens = try await ceremony()
            try? vault.save(tokens)
            session = tokens
            phase = .signedIn
        } catch Passkey.Failure.canceled {
            phase = .signedOut
        } catch {
            phase = .signedOut
            message = (error as? AccountError) == .rateLimited ? "요청이 많아요. 잠시 후 다시 시도해 주세요." : failure
        }
    }
    /// A network failure while refreshing keeps the tokens (the call fails as unavailable); only a refused
    /// refresh token signs out, so a flaky connection does not log anyone out.
    private func renewed(after stale: TokenPair) async throws -> TokenPair {
        if let session, session != stale { return session }
        if let refreshing { return try await refreshing.value }
        let task = Task { [client] in try await client.refresh(stale) }
        refreshing = task
        defer { refreshing = nil }
        let fresh = try await task.value
        try? vault.save(fresh)
        session = fresh
        return fresh
    }
    private func expire() {
        guard session != nil else { return }
        forget()
        message = "로그인이 만료됐어요. 다시 로그인해 주세요."
    }
    private func forget() {
        try? vault.clear()
        session = nil
        phase = .signedOut
    }
}
