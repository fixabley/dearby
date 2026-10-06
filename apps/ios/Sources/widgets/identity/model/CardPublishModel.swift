import Foundation
import Observation

/// Publishes the composer draft: `PUT /v1/profile`, then `POST /v1/cards`. When the profile saved but
/// the card did not, a retry publishes only the card.
@MainActor @Observable final class CardPublishModel {
    enum Phase: Equatable { case editing, loading, publishing, published(PublishedCard), failed(String) }
    var draft = CardDraft()
    private(set) var phase = Phase.editing
    private var savedProfile: AccountProfile?
    let account: AccountViewModel
    init(account: AccountViewModel) { self.account = account }

    /// Signed in: start from the saved profile.
    func load() async {
        guard account.session != nil else { return }
        phase = .loading
        do {
            let stored = try await account.authorized { [client = account.client] in try await client.profile($0) }
            draft = CardDraft(profile: stored)
            savedProfile = stored
            phase = .editing
        } catch {
            phase = .failed("프로필을 불러오지 못했어요. 다시 시도해 주세요.")
        }
    }
    /// Called after signing in from the composer: merge what was typed onto the account, then publish.
    func continueAfterSignIn() async {
        do {
            let stored = try await account.authorized { [client = account.client] in try await client.profile($0) }
            draft = draft.merged(onto: stored)
            savedProfile = stored
        } catch {
            phase = .failed("프로필을 불러오지 못했어요. 다시 시도해 주세요.")
            return
        }
        await publish()
    }
    func publish() async {
        guard draft.canPublish else { return }
        phase = .publishing
        let profile = draft.profile, card = draft.card, client = account.client
        do {
            if savedProfile != profile {
                _ = try await account.authorized { try await client.saveProfile(profile, $0) }
                savedProfile = profile
            }
            let published = try await account.authorized { try await client.publish(card, $0) }
            phase = .published(published)
        } catch AccountError.unauthorized {
            phase = .editing
        } catch AccountError.invalidInput {
            phase = .failed("입력한 내용을 확인해 주세요. 링크는 https 주소만 쓸 수 있어요.")
        } catch {
            phase = .failed(savedProfile == profile ? "프로필은 저장했어요. 명함 발행만 다시 시도해 주세요." : "발행하지 못했어요. 잠시 후 다시 시도해 주세요.")
        }
    }
}
