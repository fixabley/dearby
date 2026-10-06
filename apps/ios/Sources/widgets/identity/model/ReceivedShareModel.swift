import Foundation
import Observation

/// A received card opened from a scanned QR or a `/s/<id>` link. Reading it needs no sign-in; saving it to the
/// account's wallet does, and is offered for shares only (legacy card codes carry no share).
@MainActor @Observable final class ReceivedShareModel {
    enum Phase: Equatable { case loading, loaded(PublishedCard, [ShareActivity]), missing, failed }
    enum Saving: Equatable { case idle, saving, saved, alreadySaved, failed(String) }
    private(set) var phase = Phase.loading
    private(set) var saving = Saving.idle
    let link: ScannedLink
    let account: AccountViewModel
    init(link: ScannedLink, account: AccountViewModel) {
        self.link = link
        self.account = account
    }
    var canSave: Bool { if case .share = link, case .loaded = phase { true } else { false } }

    func load() async {
        phase = .loading
        do {
            switch link {
            case .share(let id):
                let received = try await account.client.publicShare(id)
                phase = .loaded(received.card, received.share.activities)
            case .card(let id):
                phase = .loaded(try await account.client.publicCard(id), [])
            }
        } catch AccountError.notFound {
            phase = .missing
        } catch {
            phase = .failed
        }
    }
    /// `PUT /v1/wallet/shares/:id`. Call when signed in; an expired session signs out and asks again.
    func save() async {
        guard case .share(let id) = link else { return }
        saving = .saving
        do {
            let result = try await account.authorized { [client = account.client] in try await client.saveShare(id, $0) }
            saving = result.status == "alreadySaved" ? .alreadySaved : .saved
        } catch AccountError.unauthorized {
            saving = .idle
        } catch AccountError.ownCard {
            saving = .failed("내 명함은 받은 명함에 저장하지 않아요.")
        } catch AccountError.notFound {
            phase = .missing
            saving = .idle
        } catch AccountError.conflict {
            saving = .failed("이 명함으로 받은 공유가 너무 많아 더 저장할 수 없어요.")
        } catch {
            saving = .failed("저장하지 못했어요. 잠시 후 다시 시도해 주세요.")
        }
    }
}
