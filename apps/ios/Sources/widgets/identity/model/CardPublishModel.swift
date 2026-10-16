import Foundation
import Observation

/// Publishes the composer draft with `POST /v1/cards`. The profile is read-only here (data lives in 내 프로필).
@MainActor @Observable final class CardPublishModel {
    enum Phase: Equatable { case loading, editing, publishing, published(PublishedCard), failed(String) }
    var draft: CardDraft?
    private(set) var phase = Phase.loading
    let account: AccountViewModel
    let prefill: PublishedCard?
    init(account: AccountViewModel, prefill: PublishedCard? = nil) {
        self.account = account
        self.prefill = prefill
    }
    /// Signed in: start from the saved profile. Signed out: nothing to edit until sign-in.
    func load() async {
        guard account.session != nil else { phase = .editing; return }
        phase = .loading
        do {
            let stored = try await account.authorized { [client = account.client] in try await client.profile($0) }
            if draft == nil || draft?.profile != stored { draft = CardDraft(profile: stored, prefill: prefill) }
            phase = .editing
        } catch AccountError.unauthorized {
            phase = .editing
        } catch {
            phase = .failed("프로필을 불러오지 못했어요. 다시 시도해 주세요.")
        }
    }
    func publish() async {
        guard let draft else { await load(); return }
        if let problem = draft.problem { phase = .failed(problem); return }
        phase = .publishing
        do {
            let published = try await account.authorized { [client = account.client] in try await client.publish(draft.card, $0) }
            phase = .published(published)
        } catch AccountError.unauthorized {
            phase = .editing
        } catch AccountError.rejected(let detail) {
            phase = .failed(detail)
        } catch {
            phase = .failed("발행하지 못했어요. 잠시 후 다시 시도해 주세요.")
        }
    }
    func clearFailure() { if case .failed = phase { phase = .editing } }
}
