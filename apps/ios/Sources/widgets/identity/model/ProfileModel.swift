import Foundation
import Observation

/// 내 프로필: the account's data profile (`GET`/`PUT /v1/profile`), its saved versions and history evidence.
/// Signed out, there is nothing to load and the tab offers sign-in; a failed save keeps what was typed.
@MainActor @Observable final class ProfileModel {
    enum Phase: Equatable { case signedOut, loading, loaded(AccountProfile), failed }
    private(set) var phase = Phase.loading
    /// The consent gate (contract "개인정보 동의"): on when the loaded profile says `consentRequired` or a write came back
    /// 403 `CONSENT_REQUIRED`; off once `POST /v1/me/consents` succeeds. Signed out, it is never shown.
    private(set) var consentPending = false
    /// 연락처 인증 (contract "연락처 인증" 2026-10-26): a kind drops out after its first 503 (no GitHub OAuth app / no mail server on the
    /// server) and its button is hidden for the rest of the run.
    private(set) var githubLinkAvailable = true
    private(set) var emailVerifyAvailable = true
    let account: AccountViewModel
    /// Where the policy pages live (`/privacy`, `/terms`).
    let webOrigin: URL
    init(account: AccountViewModel, webOrigin: URL = URL(string: AppOrigins.defaultWeb)!) {
        self.account = account
        self.webOrigin = webOrigin
    }
    var profile: AccountProfile? { if case .loaded(let profile) = phase { profile } else { nil } }

    func load() async {
        guard account.session != nil else { phase = .signedOut; return }
        if case .loaded = phase {} else { phase = .loading }
        do {
            let loaded = try await account.authorized { [client = account.client] in try await client.profile($0) }
            phase = .loaded(loaded)
            consentPending = loaded.consentRequired
        } catch AccountError.unauthorized {
            phase = .signedOut
            consentPending = false
        } catch {
            phase = .failed
        }
    }
    /// 동의하고 계속: records the current versions; returns an error message to show, or nil when recorded.
    func consent() async -> String? {
        do {
            try await account.authorized { [client = account.client] in
                try await client.recordConsent(privacyVersion: ConsentVersions.privacy, termsVersion: ConsentVersions.terms, $0)
            }
            consentPending = false
            await load()
            return nil
        } catch { return message(error, fallback: "동의를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.") }
    }
    /// Saves the form with `If-Match`; returns an error message to show, or nil when saved.
    func save(_ form: ProfileForm) async -> String? {
        guard form.isValid else { return "입력한 내용을 확인해 주세요." }
        let profile = form.profile
        do {
            let saved = try await account.authorized { [client = account.client] in try await client.saveProfile(profile, ifMatch: profile.version, $0) }
            phase = .loaded(saved)
            return nil
        } catch { return message(error, fallback: "저장하지 못했어요. 잠시 후 다시 시도해 주세요.") }
    }
    func versions(cursor: Int?) async throws -> ProfileVersionList {
        try await account.authorized { [client = account.client] in try await client.profileVersions(cursor: cursor, $0) }
    }
    func version(_ number: Int) async throws -> ProfileVersionDetail {
        try await account.authorized { [client = account.client] in try await client.profileVersion(number, $0) }
    }
    /// Makes a new version from an old one; the profile on screen becomes that content.
    func restore(_ number: Int) async -> String? {
        do {
            phase = .loaded(try await account.authorized { [client = account.client] in try await client.restoreProfile(number, $0) })
            return nil
        } catch { return message(error, fallback: "되돌리지 못했어요. 잠시 후 다시 시도해 주세요.") }
    }
    /// Uploads evidence for a saved history and reloads the profile (the server moves verification to pending).
    func upload(historyID: String, fileName: String, contentType: String, data: Data) async -> String? {
        do {
            _ = try await account.authorized { [client = account.client] in
                try await client.uploadEvidence(historyID: historyID, fileName: fileName, contentType: contentType, data: data, $0)
            }
            await load()
            return nil
        } catch { return message(error, fallback: "파일을 올리지 못했어요. 잠시 후 다시 시도해 주세요.") }
    }
    func deleteEvidence(historyID: String, evidenceID: String) async -> String? {
        do {
            try await account.authorized { [client = account.client] in try await client.deleteEvidence(historyID: historyID, evidenceID: evidenceID, $0) }
            await load()
            return nil
        } catch { return message(error, fallback: "파일을 지우지 못했어요. 잠시 후 다시 시도해 주세요.") }
    }
    /// 프로필 사진 (contract "프로필 사진" 2026-10-26): saved at once, outside the form's 저장; the reload brings the new `photoUrl`.
    func putPhoto(jpeg: Data) async -> String? {
        do {
            try await account.authorized { [client = account.client] in try await client.putProfilePhoto(jpeg: jpeg, $0) }
            await load()
            return nil
        } catch { return message(error, fallback: "사진을 올리지 못했어요. 잠시 후 다시 시도해 주세요.") }
    }
    func deletePhoto() async -> String? {
        do {
            try await account.authorized { [client = account.client] in try await client.deleteProfilePhoto($0) }
            await load()
            return nil
        } catch { return message(error, fallback: "사진을 지우지 못했어요. 잠시 후 다시 시도해 주세요.") }
    }
    /// GitHub 연결 step 1: the authorize page to open. Throws `AccountError`; a 503 also hides the button.
    func githubAuthorizeURL() async throws -> URL {
        do { return try await account.authorized { [client = account.client] in try await client.startGitHubLink($0) } } catch AccountError.disabled {
            githubLinkAvailable = false
            throw AccountError.disabled
        }
    }
    /// GitHub 연결 step 2 with the callback's `code`: the profile on screen becomes the server's (new `version`, GitHub row `verified`).
    /// Returns the message to show, or nil when verified.
    func completeGitHub(code: String) async -> String? {
        do {
            phase = .loaded(try await account.authorized { [client = account.client] in try await client.completeGitHubLink(code: code, $0) })
            return nil
        } catch { return message(error, fallback: "GitHub 연결에 실패했어요. 다시 시도해 주세요.") }
    }
    /// 이메일 인증번호 받기. Throws `AccountError` (`rateLimited` carries the server's wording); a 503 also hides the button.
    func requestEmailCode(_ email: String) async throws -> AccountClient.EmailChallenge {
        do { return try await account.authorized { [client = account.client] in try await client.requestEmailCode(email: email, $0) } } catch AccountError.disabled {
            emailVerifyAvailable = false
            throw AccountError.disabled
        }
    }
    /// 인증번호 확인: nil when the profile on screen now has the email `verified`, else the message to show (a wrong code names the tries left).
    /// `challengeExpired` is rethrown so the row goes back to 인증번호 받기 with its wording.
    func verifyEmail(challengeId: String, code: String) async throws -> String? {
        do {
            phase = .loaded(try await account.authorized { [client = account.client] in try await client.verifyEmailCode(challengeId: challengeId, code: code, $0) })
            return nil
        } catch AccountError.challengeExpired(let detail) {
            throw AccountError.challengeExpired(detail)
        } catch { return message(error, fallback: "인증하지 못했어요. 잠시 후 다시 시도해 주세요.") }
    }
    /// The sentence for a failed call (`rejected` detail as is; 401 signs out; 403 CONSENT_REQUIRED switches the consent gate on).
    func message(_ error: Error, fallback: String) -> String {
        switch error as? AccountError {
        case .unauthorized:
            phase = .signedOut
            return "로그인이 만료됐어요. 다시 로그인해 주세요."
        case .rejected(let detail): return detail
        case .consentRequired:
            consentPending = true
            return "시작하기 전에 개인정보 수집·이용과 이용약관에 동의해 주세요."
        case .conflict: return "프로필이 다른 곳에서 바뀌었어요. ‘원래대로’를 눌러 새로 불러온 뒤 다시 고쳐 주세요."
        case .notFound: return "찾을 수 없어요. 프로필을 다시 불러와 주세요."
        default: return fallback
        }
    }
}
