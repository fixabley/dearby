import AuthenticationServices
import Observation
import SwiftUI

/// Passkey and Apple sign-in (contract "패스키 로그인", "애플 로그인"), asked for only when an action needs an account.
@MainActor @Observable final class AccountViewModel {
    enum Phase: Equatable { case signedOut, signingIn, signedIn }
    private(set) var phase: Phase
    private(set) var message: String?
    private(set) var session: TokenPair?
    /// A first-time Apple account (202) waiting for the user to pick a new account or linking a passkey account.
    private(set) var appleLink: AccountClient.AppleLink?
    /// Build flag (Info.plist `DearbyAppleSignInEnabled`): shows the Apple button. Off until the app has the entitlement.
    let appleSignInEnabled: Bool
    let client: AccountClient
    private let vault: any TokenStore
    /// One refresh at a time: refresh tokens rotate, so a second refresh with the same token would fail.
    private var refreshing: Task<TokenPair, Error>?

    init(client: AccountClient, vault: any TokenStore, appleSignInEnabled: Bool = false) {
        self.client = client
        self.appleSignInEnabled = appleSignInEnabled
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
    /// "Apple로 계속하기": an Apple account already linked signs in; a first-time one sets `appleLink` for the user's choice.
    func signInWithApple(using controller: AuthorizationController) async {
        appleLink = nil
        await start(failure: "애플로 로그인하지 못했어요. 다시 시도해 주세요.", unavailable: "지금은 애플 로그인을 쓸 수 없어요.") { [client] in
            let challenge = try await client.appleNonce()
            let credential = try await AppleSignIn.authorize(nonce: challenge.nonce, with: controller)
            switch try await client.signInWithApple(challengeId: challenge.challengeId, credential: credential) {
            case .signedIn(let tokens): return tokens
            case .newAppleAccount(let link):
                appleLink = link
                return nil
            }
        }
    }
    /// "새 계정으로 시작": a new Dearby account for the waiting Apple account.
    func registerWithApple() async {
        guard let link = appleLink else { return }
        await start(failure: "새 계정을 만들지 못했어요. 다시 시도해 주세요.") { [client] in
            let tokens: TokenPair
            do { tokens = try await client.registerWithApple(link) } catch AccountError.unauthorized { throw AppleLinkFailure.expired }
            appleLink = nil
            return tokens
        }
    }
    /// "기존 패스키 계정에 연결": sign in with a passkey, then link the waiting Apple account to that account.
    /// If linking fails the passkey session is dropped (not stored), so no one ends up signed in but unlinked by surprise.
    func linkAppleToPasskey(using controller: AuthorizationController) async {
        guard let link = appleLink else { return }
        await start(failure: "패스키 계정에 연결하지 못했어요. 다시 시도해 주세요.") { [client] in
            let challenge = try await client.authenticationOptions()
            let credential = try await Passkey.assert(challenge.options, with: controller)
            let tokens = try await client.authenticate(challengeId: challenge.challengeId, credential: credential)
            do { try await client.linkApple(link, tokens) } catch {
                try? await client.logout(tokens)
                switch error as? AccountError {
                case .invalidInput: throw AppleLinkFailure.alreadyLinked
                case .unauthorized: throw AppleLinkFailure.expired
                default: throw error
                }
            }
            appleLink = nil
            return tokens
        }
    }
    /// Leaves the new-or-link choice; the link token is simply left to expire.
    func clearAppleLink() { appleLink = nil }
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

    /// Runs a sign-in. `ceremony` returns nil when it ends without tokens (a first-time Apple account).
    /// `unavailable`: the message for a 503 or no connection, when it differs from `failure`.
    private func start(failure: String, unavailable: String? = nil, _ ceremony: () async throws -> TokenPair?) async {
        phase = .signingIn
        message = nil
        do {
            if let tokens = try await ceremony() {
                try? vault.save(tokens)
                session = tokens
                phase = .signedIn
            } else {
                phase = .signedOut
            }
        } catch Passkey.Failure.canceled {
            phase = .signedOut
        } catch let failure as AppleLinkFailure {
            // Either way the link token cannot be used again, so the new-or-link choice ends.
            phase = .signedOut
            appleLink = nil
            message = failure == .alreadyLinked ? "이미 다른 계정과 연결돼 있어서 연결하지 못했어요."
                : "애플 로그인 시간이 지났어요. 애플로 다시 시작해 주세요."
        } catch {
            phase = .signedOut
            switch error as? AccountError {
            case .rateLimited: message = "요청이 많아요. 잠시 후 다시 시도해 주세요."
            case .unavailable where unavailable != nil: message = unavailable
            default: message = failure
            }
        }
    }
    /// After a 202: 400 when the passkey account already has another Apple account (or this Apple account was just
    /// linked elsewhere); 401 when the link token expired or was spent. The server spends the token in both cases.
    private enum AppleLinkFailure: Error { case alreadyLinked, expired }
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
