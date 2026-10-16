import Foundation
import Observation

/// Discovery over `GET /v1/activities`: server search (`q`) and cursor paging. A new query starts from the first page.
@MainActor @Observable final class ActivityViewModel {
    enum Phase: Equatable { case loading, failed, loaded }
    private(set) var phase = Phase.loading
    /// The current query's pages, in server order.
    private(set) var activities: [ActivityModel] = []
    /// The search field. Kept here so it survives leaving the tab.
    var searchText = ""
    /// The query the list shows (trimmed); nil before the first load.
    private(set) var query: String?
    private(set) var loadingMore = false
    private(set) var moreFailed = false
    // Discovery is judged at load time so the list does not change while it is on screen.
    private(set) var loadedAt = Date()
    // Example application marks, memory only and keyed by real activity IDs.
    var appliedIDs: Set<String> = []
    // The user's own participation mark, not an organizer confirmation.
    var confirmedIDs: Set<String> = []
    /// Contract #148 "신청 확인": after an official application link opened, ask once when the app comes back.
    enum ApplyAnswer { case applied, notYet, neverAsk }
    private(set) var asking: ActivityModel?
    private var opened: String?
    private var leftApp = false
    private var neverAsk: Set<String> = []
    /// Every activity seen this session, so details and applied marks outlive a new search.
    private var seen: [String: ActivityModel] = [:]
    private var cursor: Int64?
    var hasMore: Bool { cursor != nil }
    private var firstPage: Task<Void, Never>?
    private var nextPage: Task<Void, Never>?
    private let fetch: @Sendable (_ query: String, _ cursor: Int64?) async throws -> ActivityPage
    init(fetch: @escaping @Sendable (_ query: String, _ cursor: Int64?) async throws -> ActivityPage) { self.fetch = fetch }

    func activity(_ id: String) -> ActivityModel? { seen[id] }
    var appliedActivities: [ActivityModel] {
        appliedIDs.compactMap { seen[$0] }
            .sorted { ($0.schedules.first?.start ?? .distantFuture, $0.id) < ($1.schedules.first?.start ?? .distantFuture, $1.id) }
    }
    /// Shows `text`'s results from the first page, unless they are already shown or loading.
    func search(_ text: String) {
        let query = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard query != self.query else { return }
        start(query)
    }
    func reload() { start(query ?? "") }
    /// Next page while one exists; a stale page (the query changed) is dropped.
    func loadMore() {
        guard phase == .loaded, let cursor, nextPage == nil, let query else { return }
        loadingMore = true
        moreFailed = false
        nextPage = Task {
            defer { if !Task.isCancelled { loadingMore = false; nextPage = nil } }
            do {
                let page = try await fetch(query, cursor)
                guard !Task.isCancelled else { return }
                append(page)
            } catch {
                if !Task.isCancelled { moreFailed = true }
            }
        }
    }
    private func start(_ query: String) {
        firstPage?.cancel()
        nextPage?.cancel()
        nextPage = nil
        self.query = query
        phase = .loading
        activities = []
        cursor = nil
        loadingMore = false
        moreFailed = false
        firstPage = Task {
            do {
                let page = try await fetch(query, nil)
                guard !Task.isCancelled else { return }
                loadedAt = Date()
                append(page)
                phase = .loaded
            } catch {
                if !Task.isCancelled { phase = .failed }
            }
        }
    }
    private func append(_ page: ActivityPage) {
        let known = Set(activities.map(\.id))
        activities += page.items.filter { !known.contains($0.id) }
        for item in page.items { seen[item.id] = item }
        cursor = page.nextCursor
    }
    func apply(_ id: String, _ value: Bool) {
        if value { appliedIDs.insert(id) } else { appliedIDs.remove(id); confirmedIDs.remove(id) }
    }
    /// An application link for `id` was handed to the browser. Applied or "never ask" activities are not asked.
    func openedApplication(_ id: String) {
        guard !appliedIDs.contains(id), !neverAsk.contains(id) else { return }
        opened = id
        leftApp = false
    }
    /// The app went to the background, e.g. because the browser opened.
    func appLeft() { if opened != nil { leftApp = true } }
    /// The app is active again: ask about the last opened application, once.
    func appReturned() {
        guard leftApp, let id = opened else { return }
        opened = nil
        leftApp = false
        asking = seen[id]
    }
    func answer(_ answer: ApplyAnswer) {
        guard let id = asking?.id else { return }
        asking = nil
        switch answer {
        case .applied: apply(id, true)
        case .notYet: break
        case .neverAsk: neverAsk.insert(id)
        }
    }
    func confirm(_ id: String, _ value: Bool) {
        if value && appliedIDs.contains(id) { confirmedIDs.insert(id) } else { confirmedIDs.remove(id) }
    }
}
