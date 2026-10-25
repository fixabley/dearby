import AuthenticationServices
import Observation
import SwiftUI

/// Passkey, Apple and Google sign-in (contract "패스키 로그인", "애플 로그인", "구글 로그인"), asked for only when an action needs an account.
@MainActor @Observable final class AccountViewModel {
    enum Phase: Equatable { case signedOut, signingIn, signedIn }
    /// Which provider a first-time account (202) came from; the new-or-link choice and its wording follow it.
    enum LinkProvider: Equatable, Sendable { case apple, google }
    /// A first-time Apple or Google account (202) waiting for the user to pick a new account or linking a passkey account.
    struct PendingLink: Equatable, Sendable {
        let provider: LinkProvider
        let linkToken: String
        /// Apple: possibly a private relay address. Google: only when Google verified it.
        let email: String?
        var providerName: String { provider == .apple ? "애플" : "구글" }
    }
    private(set) var phase: Phase
    private(set) var message: String?
    private(set) var session: TokenPair?
    private(set) var pendingLink: PendingLink?
    /// Build flag (Info.plist `DearbyAppleSignInEnabled`): shows the Apple button. Off until the app has the entitlement.
    let appleSignInEnabled: Bool
    /// Shows the Google button: true when the build carries an iOS client ID (Info.plist `GIDClientID`) until the server
    /// answers `auth/google/nonce` with 503 (no Google client IDs configured), then hidden for the rest of this run.
    private(set) var googleSignInEnabled: Bool
    let client: AccountClient
    private let vault: any TokenStore
    /// One refresh at a time: refresh tokens rotate, so a second refresh with the same token would fail.
    private var refreshing: Task<TokenPair, Error>?

    init(client: AccountClient, vault: any TokenStore, appleSignInEnabled: Bool = false, googleSignInEnabled: Bool = false) {
        self.client = client
        self.appleSignInEnabled = appleSignInEnabled
        self.googleSignInEnabled = googleSignInEnabled
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
    /// "Apple로 계속하기": an Apple account already linked signs in; a first-time one sets `pendingLink` for the user's choice.
    func signInWithApple(using controller: AuthorizationController) async {
        pendingLink = nil
        await start(failure: "애플로 로그인하지 못했어요. 다시 시도해 주세요.", unavailable: "지금은 애플 로그인을 쓸 수 없어요.") { [client] in
            let challenge = try await client.appleNonce()
            let credential = try await AppleSignIn.authorize(nonce: challenge.nonce, with: controller)
            switch try await client.signInWithApple(challengeId: challenge.challengeId, credential: credential) {
            case .signedIn(let tokens): return tokens
            case .newAppleAccount(let link):
                return try await linkOrStart(PendingLink(provider: .apple, linkToken: link.linkToken, email: link.email), controller)
            }
        }
    }
    /// "Google로 계속하기" (contract "구글 로그인", native SDK): the server's nonce goes into the Google account sheet, the ID
    /// token comes back and signs in (200) or leaves a first-time account to link or start (202).
    /// A 503 from `auth/google/nonce` means the server has no Google client IDs: the button disappears for this run.
    func signInWithGoogle(using controller: AuthorizationController) async {
        pendingLink = nil
        await start(failure: "구글로 로그인하지 못했어요. 다시 시도해 주세요.", unavailable: "지금은 구글 로그인을 쓸 수 없어요.") { [client] in
            let nonce: String
            do { nonce = try await client.googleNonce().nonce } catch AccountError.disabled {
                googleSignInEnabled = false
                throw AccountError.disabled
            }
            let idToken = try await GoogleNativeSignIn.authorize(nonce: nonce)
            switch try await client.signInWithGoogle(idToken: idToken, nonce: nonce) {
            case .signedIn(let tokens): return tokens
            case .newGoogleAccount(let link):
                return try await linkOrStart(PendingLink(provider: .google, linkToken: link.linkToken, email: link.email), controller)
            }
        }
    }
    /// 2026-10-17 decision: a first-time Apple or Google account links to the passkey account already on this device (one
    /// Face ID pass, no question) and, with no such passkey, starts a separate account right away. The system reports "no
    /// passkey here" and a closed sheet alike (`canceled`), so a cancel that returns before any sheet could show (under
    /// 400 ms) counts as "no passkey"; a later one leaves the new-or-link choice open for the person to decide.
    private func linkOrStart(_ link: PendingLink, _ controller: AuthorizationController) async throws -> TokenPair? {
        let challenge = try await client.authenticationOptions()
        // The clock covers only the OS request, not the challenge round-trip, so a slow network cannot look like a cancel.
        let started = ContinuousClock.now
        let credential: Passkey.Credential
        do {
            credential = try await Passkey.assert(challenge.options, with: controller, localOnly: true)
        } catch Passkey.Failure.canceled where ContinuousClock.now - started < .milliseconds(400) {
            return try await startAccount(link)
        } catch Passkey.Failure.canceled {
            pendingLink = link
            return nil
        }
        return try await finishLink(link, challengeId: challenge.challengeId, credential: credential)
    }
    private func startAccount(_ link: PendingLink) async throws -> TokenPair {
        let tokens: TokenPair
        do {
            tokens = switch link.provider {
            case .apple: try await client.registerWithApple(.init(linkToken: link.linkToken, email: link.email))
            case .google: try await client.registerWithGoogle(.init(linkToken: link.linkToken, email: link.email, displayName: nil))
            }
        } catch AccountError.unauthorized { throw LinkFailure.expired }
        pendingLink = nil
        return tokens
    }
    /// Passkey sign-in, then the waiting account is linked to that account. If linking fails the passkey session is
    /// dropped (not stored), so no one ends up signed in but unlinked by surprise.
    private func linkPasskeyAccount(_ link: PendingLink, _ controller: AuthorizationController) async throws -> TokenPair {
        let challenge = try await client.authenticationOptions()
        let credential = try await Passkey.assert(challenge.options, with: controller)
        return try await finishLink(link, challengeId: challenge.challengeId, credential: credential)
    }
    private func finishLink(_ link: PendingLink, challengeId: String, credential: Passkey.Credential) async throws -> TokenPair {
        let tokens = try await client.authenticate(challengeId: challengeId, credential: credential)
        do {
            switch link.provider {
            case .apple: try await client.linkApple(.init(linkToken: link.linkToken, email: link.email), tokens)
            case .google: try await client.linkGoogle(.init(linkToken: link.linkToken, email: link.email, displayName: nil), tokens)
            }
        } catch {
            try? await client.logout(tokens)
            switch error as? AccountError {
            case .invalidInput, .rejected: throw LinkFailure.alreadyLinked
            case .unauthorized: throw LinkFailure.expired
            default: throw error
            }
        }
        pendingLink = nil
        return tokens
    }
    /// "새 계정으로 시작": a new Dearby account for the waiting Apple or Google account.
    func registerPendingAccount() async {
        guard let link = pendingLink else { return }
        await start(failure: "새 계정을 만들지 못했어요. 다시 시도해 주세요.") { try await startAccount(link) }
    }
    /// "기존 패스키 계정에 연결": sign in with a passkey, then link the waiting account to that account.
    func linkPendingToPasskey(using controller: AuthorizationController) async {
        guard let link = pendingLink else { return }
        await start(failure: "패스키 계정에 연결하지 못했어요. 다시 시도해 주세요.") { try await linkPasskeyAccount(link, controller) }
    }
    /// Leaves the new-or-link choice; the link token is simply left to expire.
    func clearPendingLink() { pendingLink = nil }
    /// Signing out always forgets the device tokens, even if the server call fails.
    /// Runs with the session before 로그아웃 ends it (this device's push token leaves the account, contract "즐겨찾기(하트)·새 모집 푸시 알림").
    @ObservationIgnored var willSignOut: (@MainActor (TokenPair) async -> Void)?
    func signOut() async {
        if let session {
            await willSignOut?(session)
            try? await client.logout(session)
        }
        forget()
    }
    enum DeletionResult: Equatable {
        case deleted, signedOut, failed
        /// What the screens tell the person afterwards (프로필·설정 share it).
        var notice: String {
            switch self {
            case .deleted: "계정을 삭제했어요. 기기에 남은 패스키는 설정 > 암호에서 지울 수 있어요."
            case .signedOut: "로그인이 만료됐어요. 다시 로그인해 주세요."
            case .failed: "계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요."
            }
        }
    }
    /// True while `deleteAccount` waits for the server; the delete button stays disabled.
    private(set) var deleting = false
    /// "계정 삭제" (contract "계정 삭제"): one request at a time. 204 forgets the tokens without `/auth/logout`, since
    /// the server already dropped the refresh token. A refused refresh signs out (`authorized`); any other failure keeps the session.
    func deleteAccount() async -> DeletionResult {
        guard !deleting else { return .failed }
        deleting = true
        defer { deleting = false }
        do {
            try await authorized { [client] in try await client.deleteAccount($0) }
            forget()
            return .deleted
        } catch AccountError.unauthorized {
            return .signedOut
        } catch {
            return .failed
        }
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

    /// Runs a sign-in. `ceremony` returns nil when it ends without tokens (a first-time Apple or Google account).
    /// `unavailable`: the message for a 503, no connection or a disabled provider, when it differs from `failure`.
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
        } catch let failure as LinkFailure {
            // Either way the link token cannot be used again, so the new-or-link choice ends.
            let provider = pendingLink?.providerName ?? "소셜"
            phase = .signedOut
            pendingLink = nil
            message = failure == .alreadyLinked ? "이미 다른 계정과 연결돼 있어서 연결하지 못했어요."
                : "\(provider) 로그인 시간이 지났어요. 처음부터 다시 시작해 주세요."
        } catch {
            phase = .signedOut
            switch error as? AccountError {
            case .rateLimited: message = "요청이 많아요. 잠시 후 다시 시도해 주세요."
            case .unavailable, .disabled: message = unavailable ?? failure
            default: message = failure
            }
        }
    }
    /// After a 202: 400 when the passkey account already has another account of that provider (or this account was just
    /// linked elsewhere); 401 when the link token expired or was spent. The server spends the token in both cases.
    private enum LinkFailure: Error { case alreadyLinked, expired }
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
