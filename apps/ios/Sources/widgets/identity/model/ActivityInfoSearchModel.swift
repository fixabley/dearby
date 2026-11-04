import Foundation
import Observation

/// Search for one picker step (units, roles or jobs), debounced; `owner` is the unit whose roles are listed (a blank query lists them all).
@MainActor @Observable final class ActivityInfoSearchModel {
    let kind: AccountClient.ActivityInfoKind
    let owner: HistoryOwner?
    var query = "" { didSet { schedule() } }
    private(set) var items: [ActivityInfoItem] = []
    private(set) var loading = false
    private(set) var failed = false
    private let account: AccountViewModel
    private var task: Task<Void, Never>?
    init(kind: AccountClient.ActivityInfoKind, owner: HistoryOwner? = nil, account: AccountViewModel) {
        self.kind = kind
        self.owner = owner
        self.account = account
        schedule(delay: 0)
    }
    private func schedule(delay: UInt64 = 300_000_000) {
        task?.cancel()
        let text = query
        task = Task { [weak self] in
            try? await Task.sleep(nanoseconds: delay)
            guard !Task.isCancelled else { return }
            await self?.search(text)
        }
    }
    private func search(_ text: String) async {
        loading = true
        failed = false
        do {
            let page = try await account.authorized { [account, kind, owner] session in
                try await account.client.searchActivityInfo(kind, query: text, owner: owner, session)
            }
            if !Task.isCancelled { items = page.items }
        } catch {
            if !Task.isCancelled { failed = true }
        }
        loading = false
    }
}
