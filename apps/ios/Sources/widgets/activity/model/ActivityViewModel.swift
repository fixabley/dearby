import Foundation
import Observation

/// Discovery as a program accordion (contract "발견 프로그램 묶음 — 2026-10-18"): `GET /v1/discovery/programs` with server search (`q`)
/// and cursor paging; an expanded program loads its open activities with `GET /v1/activities?programId=&open=true` (the server judges
/// openness, contract "열린 활동 판정 일원화") and, when none is open, its past activities of the last two years ("모집 중인 프로그램만 보기").
/// Also the session's applied / confirmed marks and the apply-confirmation prompt, shared with 내 활동 and the detail screen.
@MainActor @Observable final class ActivityViewModel {
    enum Phase: Equatable { case loading, failed, loaded }
    private(set) var phase = Phase.loading
    /// The current query's programs, in server order (nearest deadline first).
    private(set) var programs: [ProgramSummary] = []
    /// Expanded programs; memory only. Programs with one open activity or a matched activity name start expanded.
    private(set) var expanded: Set<Int64> = []
    /// Open activities per program, once loaded.
    private(set) var openActivities: [Int64: [ActivityModel]] = [:]
    /// Past activities (last two years, latest start first) for an expanded program without open ones.
    private(set) var pastActivities: [Int64: [ActivityModel]] = [:]
    /// Activities whose recruitment (or activity, without a window) has not started yet, nearest first; same programs as `pastActivities`.
    /// Both are loaded for every expanded program; the page shows `모집 예정` always and `지난 활동` only while `openOnly` is off (2026-10-19 밤).
    private(set) var upcomingActivities: [Int64: [ActivityModel]] = [:]
    /// `마감 숨기기` (contract "모집 중인 프로그램만 보기"): on by default, memory only. Off also lists programs active in the last two years.
    var openOnly = true { didSet { if openOnly != oldValue { start(query ?? "") } } }
    private(set) var loadingPrograms: Set<Int64> = []
    private(set) var failedPrograms: Set<Int64> = []
    /// The search field. Kept here so it survives leaving the tab.
    var searchText = ""
    /// The query the list shows (trimmed); nil before the first load.
    private(set) var query: String?
    private(set) var loadingMore = false
    private(set) var moreFailed = false
    /// Pull to refresh failed while a list was on screen: the list stays and the page shows a passing notice.
    private(set) var refreshFailed = false
    // Discovery is judged at load time so the list does not change while it is on screen.
    private(set) var loadedAt = Date()
    // Example application marks, memory only and keyed by real activity IDs.
    var appliedIDs: Set<String> = []
    // The user's own participation mark, not an organizer confirmation.
    var confirmedIDs: Set<String> = []
    /// The user's stage marks (contract "내 활동 진행 트랙"), memory only like `appliedIDs`: activity id → stage id → mark.
    var stageMarks: [String: [String: StageMark]] = [:]
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
    private var programLoads: [Int64: Task<Void, Never>] = [:]
    private let fetchPrograms: @Sendable (_ query: String, _ cursor: Int64?, _ openOnly: Bool) async throws -> ProgramPage
    /// `open` true asks for open activities only; false for every activity of the program (past list).
    private let fetchActivities: @Sendable (_ programID: Int64, _ open: Bool) async throws -> ActivityPage
    init(programs: @escaping @Sendable (_ query: String, _ cursor: Int64?, _ openOnly: Bool) async throws -> ProgramPage,
         activities: @escaping @Sendable (_ programID: Int64, _ open: Bool) async throws -> ActivityPage) {
        fetchPrograms = programs
        fetchActivities = activities
    }

    func activity(_ id: String) -> ActivityModel? { seen[id] }
    /// Applied activities with their tracks, nearest next stage first; date-less and ended tracks last, then by id.
    func appliedProgress(now: Date) -> [AppliedActivity] {
        appliedIDs.compactMap { seen[$0] }.map { AppliedActivity(activity: $0, progress: progress($0, now: now)) }
            .sorted { ($0.progress?.nextStart ?? .distantFuture, $0.id) < ($1.progress?.nextStart ?? .distantFuture, $1.id) }
    }
    func progress(_ activity: ActivityModel, now: Date) -> ActivityProgress? {
        ActivityStages.build(activity, marks: stageMarks[activity.id] ?? [:], now: now)
    }
    func stageMark(_ activityID: String, _ stageID: String) -> StageMark { stageMarks[activityID]?[stageID] ?? .wait }
    func markStage(_ activityID: String, _ stageID: String, _ mark: StageMark) { stageMarks[activityID, default: [:]][stageID] = mark }
    /// Shows `text`'s programs from the first page, unless they are already shown or loading.
    func search(_ text: String) {
        let query = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard query != self.query else { return }
        start(query)
    }
    func reload() { start(query ?? "") }
    /// Pull to refresh (contract "발견 탭 스크롤 동작" 2026-10-19 밤): the first page again for the current query and toggle. The list stays on
    /// screen until the page arrives; programs that were expanded stay expanded and load their activities again. Failure shows the usual error.
    func refresh() async {
        let query = query ?? ""
        let kept = expanded
        firstPage?.cancel()
        nextPage?.cancel()
        nextPage = nil
        programLoads.values.forEach { $0.cancel() }
        programLoads = [:]
        loadingMore = false
        moreFailed = false
        refreshFailed = false
        let task = Task {
            do {
                let page = try await fetchPrograms(query, nil, openOnly)
                guard !Task.isCancelled else { return }
                loadedAt = Date()
                programs = []
                expanded = []
                cursor = nil
                loadingPrograms = []
                failedPrograms = []
                phase = .loaded
                append(page)
                let shown = Set(programs.map(\.id))
                expanded.formUnion(kept.intersection(shown))
                // Activities of programs no longer listed are dropped; the kept ones stay until their reload lands.
                openActivities = openActivities.filter { shown.contains($0.key) }
                upcomingActivities = upcomingActivities.filter { shown.contains($0.key) }
                pastActivities = pastActivities.filter { shown.contains($0.key) }
                // Programs `append` just started loading are skipped by the in-flight guard.
                for id in expanded { loadActivities(id, reload: true) }
            } catch {
                guard !Task.isCancelled else { return }
                // A list already on screen stays; only a refresh with nothing shown falls back to the error screen.
                if phase == .loaded && !programs.isEmpty { refreshFailed = true } else { phase = .failed }
            }
        }
        firstPage = task
        await task.value
    }
    func clearRefreshFailure() { refreshFailed = false }
    /// Next page of programs while one exists; a stale page (the query changed) is dropped.
    func loadMore() {
        guard phase == .loaded, let cursor, nextPage == nil, let query else { return }
        loadingMore = true
        moreFailed = false
        nextPage = Task {
            defer { if !Task.isCancelled { loadingMore = false; nextPage = nil } }
            do {
                let page = try await fetchPrograms(query, cursor, openOnly)
                guard !Task.isCancelled else { return }
                append(page)
            } catch {
                if !Task.isCancelled { moreFailed = true }
            }
        }
    }
    func isExpanded(_ programID: Int64) -> Bool { expanded.contains(programID) }
    /// Opens or closes one program; opening loads its activities the first time (or after a failure).
    func toggle(_ programID: Int64) {
        if expanded.contains(programID) { expanded.remove(programID); return }
        expanded.insert(programID)
        loadActivities(programID)
    }
    func retryActivities(_ programID: Int64) { loadActivities(programID) }
    private func start(_ query: String) {
        firstPage?.cancel()
        nextPage?.cancel()
        nextPage = nil
        programLoads.values.forEach { $0.cancel() }
        programLoads = [:]
        self.query = query
        phase = .loading
        programs = []
        expanded = []
        openActivities = [:]
        pastActivities = [:]
        upcomingActivities = [:]
        loadingPrograms = []
        failedPrograms = []
        cursor = nil
        loadingMore = false
        moreFailed = false
        firstPage = Task {
            do {
                let page = try await fetchPrograms(query, nil, openOnly)
                guard !Task.isCancelled else { return }
                loadedAt = Date()
                append(page)
                phase = .loaded
            } catch {
                if !Task.isCancelled { phase = .failed }
            }
        }
    }
    private func append(_ page: ProgramPage) {
        let known = Set(programs.map(\.id))
        let fresh = page.items.filter { !known.contains($0.id) }
        programs += fresh
        cursor = page.nextCursor
        // Auto-expanding a single open activity applies only while the list is limited to recruiting programs.
        for program in fresh where (openOnly && program.openActivityCount == 1) || !program.matchedActivityIDs.isEmpty {
            expanded.insert(program.id)
            loadActivities(program.id)
        }
    }
    /// `reload` fetches again even when the program's activities are already shown (pull to refresh keeps the old ones until then).
    private func loadActivities(_ programID: Int64, reload: Bool = false) {
        guard reload || openActivities[programID] == nil, programLoads[programID] == nil else { return }
        loadingPrograms.insert(programID)
        failedPrograms.remove(programID)
        programLoads[programID] = Task {
            defer { if !Task.isCancelled { loadingPrograms.remove(programID); programLoads[programID] = nil } }
            do {
                let page = try await fetchActivities(programID, true)
                guard !Task.isCancelled else { return }
                // The server already applied the open rule (`open=true`); the client keeps every returned activity.
                for item in page.items { seen[item.id] = item }
                openActivities[programID] = page.items
                // `모집 예정` (recruitment or activity not started yet, nearest first) and `지난 활동` (started within the
                // last three years, latest first) are loaded for every expanded program; the page decides what to show
                // (upcoming always, past only while `마감 숨기기` is off). Contract "펼침" (2026-10-19 밤).
                do {
                    let others = try await fetchActivities(programID, false)
                    guard !Task.isCancelled else { return }
                    let openIDs = Set(page.items.map(\.id))
                    let upcoming = others.items.filter { !openIDs.contains($0.id) && ($0.upcomingStart ?? .distantPast) > loadedAt }
                        .sorted { ($0.upcomingStart ?? .distantFuture, $0.title) < ($1.upcomingStart ?? .distantFuture, $1.title) }
                    let since = Calendar(identifier: .gregorian).date(byAdding: .year, value: -3, to: loadedAt) ?? loadedAt
                    let excluded = openIDs.union(upcoming.map(\.id))
                    let recent = others.items.filter { !excluded.contains($0.id) && ($0.activityStage?.start ?? .distantPast) >= since }
                        .sorted { ($0.activityStage?.start ?? .distantPast, $0.title) > ($1.activityStage?.start ?? .distantPast, $1.title) }
                    for item in upcoming + recent { seen[item.id] = item }
                    upcomingActivities[programID] = upcoming
                    pastActivities[programID] = recent
                }
            } catch {
                if !Task.isCancelled { failedPrograms.insert(programID) }
            }
        }
    }
    func apply(_ id: String, _ value: Bool) {
        if value { appliedIDs.insert(id) } else { appliedIDs.remove(id); confirmedIDs.remove(id); stageMarks[id] = nil }
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
