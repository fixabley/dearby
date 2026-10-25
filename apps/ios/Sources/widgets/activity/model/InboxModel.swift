import Foundation
import Observation

/// 알림 목록 (contract "알림 목록(발견 우상단 종)" 2026-10-25): signed in only. `unread` is the bell's badge; the page reads the program groups
/// (cursor paging) and only the user's tap expands a card. Opening a row or `모두 읽음` marks it read on screen at once and then on the server
/// (best effort: the next load shows the server's state). A server without the inbox (404) shows the empty page and no badge.
@MainActor @Observable final class InboxModel {
    enum Phase: Equatable { case signedOut, loading, loaded, failed }
    private(set) var phase = Phase.signedOut
    private(set) var groups: [InboxGroup] = []
    private(set) var unread = 0
    private(set) var expanded: Set<Int64> = []
    private(set) var loadingMore = false
    private var cursor: String?
    var hasMore: Bool { cursor != nil }
    private var server: InboxServer?

    func signedIn(_ server: InboxServer) async {
        self.server = server
        await refreshCount()
    }
    func signedOut() {
        server = nil
        phase = .signedOut
        groups = []
        unread = 0
        expanded = []
        cursor = nil
    }
    /// The badge: on sign-in, on return to the app and when the page opens.
    func refreshCount() async {
        guard let server else { return }
        // A failure keeps the last count.
        do { unread = try await server.unreadCount() ?? 0 } catch {}
    }
    /// The first page; the list on screen stays until it arrives.
    func load() async {
        guard let server else { return }
        if groups.isEmpty { phase = .loading }
        do {
            let page = try await server.load(nil)
            guard self.server != nil else { return }
            groups = page?.groups ?? []
            cursor = page?.nextCursor
            phase = .loaded
            expanded.formIntersection(groups.map(\.id))
        } catch {
            if groups.isEmpty { phase = .failed }
        }
        await refreshCount()
    }
    func loadMore() async {
        guard let server, let cursor, !loadingMore else { return }
        loadingMore = true
        defer { loadingMore = false }
        guard let page = try? await server.load(cursor) else { return }
        let known = Set(groups.map(\.id))
        groups += page.groups.filter { !known.contains($0.id) }
        self.cursor = page.nextCursor
    }
    func toggle(_ programID: Int64) {
        if expanded.contains(programID) { expanded.remove(programID) } else { expanded.insert(programID) }
    }
    /// A row was opened (or its push tapped): read now, sent in the background.
    func read(_ id: Int64) {
        if let group = groups.firstIndex(where: { $0.items.contains { $0.id == id } }),
           let item = groups[group].items.firstIndex(where: { $0.id == id }), !groups[group].items[item].read {
            groups[group].items[item].read = true
            groups[group].unread = max(groups[group].unread - 1, 0)
            unread = max(unread - 1, 0)
        }
        guard let server else { return }
        Task {
            try? await server.read(id)
            await refreshCount()
        }
    }
    /// `모두 읽음`.
    func readAll() {
        guard let server else { return }
        for index in groups.indices {
            groups[index].unread = 0
            for item in groups[index].items.indices { groups[index].items[item].read = true }
        }
        unread = 0
        Task {
            try? await server.read(nil)
            await refreshCount()
        }
    }
}

/// The signed-in account's inbox calls, built by the app with the account's token refresh.
struct InboxServer: Sendable {
    /// A page from `cursor` (nil: the first); nil when the server has no inbox (404).
    let load: @Sendable (String?) async throws -> InboxPage?
    /// Nil on 404.
    let unreadCount: @Sendable () async throws -> Int?
    /// One notification, or all with nil.
    let read: @Sendable (Int64?) async throws -> Void
}
