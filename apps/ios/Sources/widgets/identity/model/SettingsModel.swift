import AuthenticationServices
import Observation
import SwiftUI

/// 설정 (contract "설정" 2026-10-19 밤): the account summary, linked Apple/Google accounts and this account's passkeys, with
/// linking, unlinking, renaming, deleting and adding. Signed out there is nothing to load. Errors are the screen's to show (`notice`).
@MainActor @Observable final class SettingsModel {
    enum Phase: Equatable { case signedOut, loading, loaded, failed }
    private(set) var phase = Phase.loading
    private(set) var summary: AccountSummary?
    private(set) var identities: [LinkedIdentity] = []
    private(set) var passkeys: [PasskeySummary] = []
    /// True while a change (link, unlink, rename, delete, add) is in flight; the rows are disabled meanwhile.
    private(set) var busy = false
    /// A sentence for the person after a failed change; nil once they dismiss it.
    var notice: String?
    let account: AccountViewModel
    init(account: AccountViewModel) { self.account = account }

    /// Passkeys plus linked accounts at one: that one cannot be removed (contract "마지막 수단"); the server also answers 409.
    var isLastSignInMethod: Bool { (summary?.signInMethods.count ?? 0) <= 1 }
    func identity(_ provider: String) -> LinkedIdentity? { identities.first { $0.provider == provider } }

    func load() async {
        guard account.session != nil else { phase = .signedOut; return }
        if phase != .loaded { phase = .loading }
        do {
            let client = account.client
            let (summary, identities, passkeys) = try await account.authorized { session in
                async let summary = client.me(session)
                async let identities = client.identities(session)
                async let passkeys = client.passkeys(session)
                return try await (summary, identities, passkeys)
            }
            self.summary = summary
            self.identities = identities
            self.passkeys = passkeys
            phase = .loaded
        } catch AccountError.unauthorized {
            phase = .signedOut
        } catch {
            phase = .failed
        }
    }
    /// 연결하기: the provider's native sign-in, then `POST /v1/me/identities/{provider}` with the link token. A provider account that
    /// already has its own Dearby account (200) cannot be linked; those tokens are dropped and the current session stays.
    func link(_ provider: AccountViewModel.LinkProvider, using controller: AuthorizationController) async {
        let client = account.client
        await change(failure: "연결하지 못했어요. 다시 시도해 주세요.") {
            switch provider {
            case .apple:
                let challenge = try await client.appleNonce()
                let credential = try await AppleSignIn.authorize(nonce: challenge.nonce, with: controller)
                switch try await client.signInWithApple(challengeId: challenge.challengeId, credential: credential) {
                case .signedIn(let other): try? await client.logout(other); throw LinkFailure.ownAccount
                case .newAppleAccount(let link): try await account.authorized { try await client.linkApple(link, $0) }
                }
            case .google:
                let nonce = try await client.googleNonce().nonce
                let idToken = try await GoogleNativeSignIn.authorize(nonce: nonce)
                switch try await client.signInWithGoogle(idToken: idToken, nonce: nonce) {
                case .signedIn(let other): try? await client.logout(other); throw LinkFailure.ownAccount
                case .newGoogleAccount(let link): try await account.authorized { try await client.linkGoogle(link, $0) }
                }
            }
        }
    }
    /// 연결 해제 (`DELETE /v1/me/identities/{provider}`).
    func unlink(_ provider: String) async {
        let client = account.client
        await change(failure: "연결을 해제하지 못했어요. 다시 시도해 주세요.") { try await account.authorized { try await client.unlinkIdentity(provider, $0) } }
    }
    /// 이름 바꾸기: 1~60 characters after trimming.
    func rename(_ passkey: PasskeySummary, to label: String) async {
        let trimmed = label.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (1...60).contains(trimmed.count) else { notice = "이름은 1자 이상 60자 이하로 적어 주세요."; return }
        let client = account.client
        await change(failure: "이름을 바꾸지 못했어요. 다시 시도해 주세요.") {
            try await account.authorized { _ = try await client.renamePasskey(passkey.id, label: trimmed, $0) }
        }
    }
    func delete(_ passkey: PasskeySummary) async {
        let client = account.client
        await change(failure: "패스키를 지우지 못했어요. 다시 시도해 주세요.") { try await account.authorized { try await client.deletePasskey(passkey.id, $0) } }
    }
    /// 패스키 추가: the registration ceremony with the session's Bearer, so the new passkey joins this account.
    func addPasskey(using controller: AuthorizationController) async {
        let client = account.client
        await change(failure: "패스키를 추가하지 못했어요. 다시 시도해 주세요.") {
            let challenge = try await account.authorized { try await client.registrationOptions(session: $0) }
            let credential = try await Passkey.register(challenge.options, with: controller)
            try await account.authorized { try await client.addPasskey(challengeId: challenge.challengeId, credential: credential, $0) }
        }
    }
    /// 로그아웃: the server call and the device tokens, then the screen falls back to the signed-out state.
    func signOut() async {
        await account.signOut()
        phase = .signedOut
    }

    private enum LinkFailure: Error { case ownAccount }
    /// Runs one change, then reloads. A cancelled system sheet is not an error.
    private func change(failure: String, _ call: () async throws -> Void) async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        notice = nil
        do {
            try await call()
            await load()
        } catch Passkey.Failure.canceled {
        } catch LinkFailure.ownAccount {
            notice = "이 계정은 이미 다른 Dearby 계정으로 쓰고 있어서 연결할 수 없어요."
        } catch {
            switch error as? AccountError {
            case .lastSignInMethod: notice = "마지막 로그인 수단은 지울 수 없어요. 다른 로그인 수단을 먼저 추가해 주세요."
            case .unauthorized: phase = .signedOut
            case .rejected(let detail): notice = detail
            case .invalidInput: notice = "이미 다른 계정과 연결돼 있어서 연결하지 못했어요."
            case .rateLimited: notice = "요청이 많아요. 잠시 후 다시 시도해 주세요."
            case .disabled: notice = "지금은 이 로그인을 쓸 수 없어요."
            default: notice = failure
            }
        }
    }
}
