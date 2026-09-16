/// Screen-local routing lifetime, not a Widget's rendered NoticeDetailState or a repository cache.
@MainActor
struct NoticeDetailRouteState {
    struct Key: Hashable {
        let id: String
        let generation: Int
    }

    private(set) var loadedKey: Key?
    private(set) var viewModel: NoticeDetailViewModel?

    mutating func load(_ key: Key, makeViewModel: (String) throws -> NoticeDetailViewModel?) {
        guard loadedKey != key else { return }
        viewModel = try? makeViewModel(key.id)
        loadedKey = key
    }
}
