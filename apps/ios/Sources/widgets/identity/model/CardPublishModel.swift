import Foundation
import Observation

/// Publishes the composer draft with `POST /v1/cards`, or with a `prefill` card saves it over that same card with `PUT /v1/cards/:id`
/// (contract "명함 고치기는 같은 명함을 수정" 2026-10-20). `prefill` is fixed per model, so 새 명함 (no prefill) can only POST.
/// The profile is read-only here (data lives in 내 프로필).
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
            let published = try await account.authorized { [client = account.client, editingID = prefill?.id] in
                if let editingID { try await client.updateCard(editingID, draft.card, $0) } else { try await client.publish(draft.card, $0) }
            }
            phase = .published(published)
        } catch AccountError.unauthorized {
            phase = .editing
        } catch AccountError.rejected(let detail) {
            phase = .failed(detail)
        } catch AccountError.notFound where prefill != nil {
            phase = .failed("이 명함은 더 이상 없어요. 내 명함 목록을 다시 확인해 주세요.")
        } catch {
            phase = .failed(prefill == nil ? "발행하지 못했어요. 잠시 후 다시 시도해 주세요." : "저장하지 못했어요. 잠시 후 다시 시도해 주세요.")
        }
    }
    func clearFailure() { if case .failed = phase { phase = .editing } }
}
