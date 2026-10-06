import Foundation
import Observation

/// 내 프로필: the account's private profile (`GET`/`PUT /v1/profile`). Signed out, there is nothing to load and the
/// tab offers card making or sign-in; a failed save keeps what was typed.
@MainActor @Observable final class ProfileModel {
    enum Phase: Equatable { case signedOut, loading, loaded(AccountProfile), failed }
    private(set) var phase = Phase.loading
    let account: AccountViewModel
    init(account: AccountViewModel) { self.account = account }

    func load() async {
        guard account.session != nil else { phase = .signedOut; return }
        if case .loaded = phase {} else { phase = .loading }
        do {
            phase = .loaded(try await account.authorized { [client = account.client] in try await client.profile($0) })
        } catch AccountError.unauthorized {
            phase = .signedOut
        } catch {
            phase = .failed
        }
    }
    /// Saves the form; returns an error message to show, or nil when saved.
    func save(_ form: ProfileForm) async -> String? {
        guard form.isValid else { return "입력한 내용을 확인해 주세요." }
        let profile = form.profile
        do {
            let saved = try await account.authorized { [client = account.client] in try await client.saveProfile(profile, $0) }
            phase = .loaded(saved)
            return nil
        } catch AccountError.unauthorized {
            phase = .signedOut
            return "로그인이 만료됐어요. 다시 로그인해 주세요."
        } catch AccountError.invalidContacts {
            return "전화번호와 이메일을 확인해 주세요."
        } catch AccountError.invalidInput {
            return "입력한 내용을 확인해 주세요. 링크는 https 주소만 쓸 수 있어요."
        } catch {
            return "저장하지 못했어요. 잠시 후 다시 시도해 주세요."
        }
    }
}
