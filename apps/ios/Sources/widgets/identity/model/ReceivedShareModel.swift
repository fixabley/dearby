import Foundation
import Observation

/// A received card opened from a scanned QR or a `/s/<id>` link. Reading it needs no sign-in.
@MainActor @Observable final class ReceivedShareModel {
    enum Phase: Equatable { case loading, loaded(PublishedCard, [ShareActivity]), missing, failed }
    private(set) var phase = Phase.loading
    let link: ScannedLink
    private let client: AccountClient
    init(link: ScannedLink, client: AccountClient) {
        self.link = link
        self.client = client
    }
    func load() async {
        phase = .loading
        do {
            switch link {
            case .share(let id):
                let received = try await client.publicShare(id)
                phase = .loaded(received.card, received.share.activities)
            case .card(let id):
                phase = .loaded(try await client.publicCard(id), [])
            }
        } catch AccountError.notFound {
            phase = .missing
        } catch {
            phase = .failed
        }
    }
}
