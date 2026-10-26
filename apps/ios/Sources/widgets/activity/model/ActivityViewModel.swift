import Foundation
import Observation

/// Discovery as a program accordion (contract "발견 프로그램 묶음 — 2026-10-18"): `GET /v1/discovery/programs` with server search (`q`)
/// and cursor paging, both read with the chosen 활동 상태 (contract "활동 상태" 2026-10-26); an expanded program loads its activities in
/// those states with `GET /v1/activities?programId=&status=` (the server judges the state) and shows them by state.
/// Also 내 활동 — the applied activities and their stage marks — and the apply-confirmation prompt, shared with 내 활동 and the detail screen.
/// 내 활동 storage (contract "내 활동 서버 저장" 2026-10-22): signed out, the marks live in this model's memory as before (the app has no
/// device copy of them); signed in, `signedIn(_:)` uploads those once with `import`, then the server's list replaces them and every
/// change is sent to `/v1/me/activities…` as it is made. A failed change shows `notice` and reloads the server's list.
@MainActor @Observable final class ActivityViewModel {
    enum Phase: Equatable { case loading, failed, loaded }
    private(set) var phase = Phase.loading
    /// The current query's programs, in server order (nearest deadline first).
    private(set) var programs: [ProgramSummary] = []
    /// Expanded programs; memory only. Only the user's tap expands one (contract "태그 아이콘+라벨·분야 1개·자동 펼침 없음" 2026-10-24: never on
    /// first entry, search results, 추천 or a link's return); a matched activity name shows as `맞는 활동 n개` on the collapsed card.
    private(set) var expanded: Set<Int64> = []
    /// Each loaded program's activities by state; read through `activities(_:for:)`.
    private var loadedActivities: [Int64: ProgramActivities] = [:]
    /// 발견 상태 필터 (contract "활동 상태" 2026-10-26, replacing `마감 숨기기`): `지원 가능` by default, and choosing none falls back to it.
    /// Kept on the device (`dearby.discovery.statusGroups`, comma-separated ids); a change reloads the list for the current search.
    private(set) var statusGroups = ActivityViewModel.storedGroups()
    private static let groupsKey = "dearby.discovery.statusGroups"
    private static func storedGroups() -> Set<StatusGroup> {
        let stored = (UserDefaults.standard.string(forKey: groupsKey) ?? "").split(separator: ",").compactMap { StatusGroup(rawValue: String($0)) }
        return stored.isEmpty ? [.available] : Set(stored)
    }
    func setStatusGroups(_ groups: Set<StatusGroup>) {
        let groups = groups.isEmpty ? [.available] : groups
        guard groups != statusGroups else { return }
        statusGroups = groups
        UserDefaults.standard.set(StatusGroup.allCases.filter(groups.contains).map(\.rawValue).joined(separator: ","), forKey: Self.groupsKey)
        start(query ?? "")
    }
    private static func statuses(_ groups: Set<StatusGroup>) -> [ActivityStatus] { StatusGroup.allCases.filter(groups.contains).flatMap(\.statuses) }
    /// 발견 `프로그램 | 이벤트` (user 2026-10-26): 프로그램 by default; the choice stays on the device (`dearby.discovery.kind`, a new key).
    /// Switching reloads the list and the expanded programs' activities (both read with `kind`) for the current search. The queue row
    /// and 추천 do not change.
    var kind = ActivityViewModel.storedKind() {
        didSet {
            guard kind != oldValue else { return }
            UserDefaults.standard.set(kind.rawValue, forKey: Self.kindKey)
            categories = Self.storedCategories(kind)
            start(query ?? "")
        }
    }
    private static let kindKey = "dearby.discovery.kind"
    private static func storedKind() -> DiscoveryKind { DiscoveryKind(rawValue: UserDefaults.standard.string(forKey: kindKey) ?? "") ?? .program }
    /// 종류 필터 (contract "종류 필터" 2026-10-26): none chosen is every 종류 (미분류 included). Kept per side on the device
    /// (`dearby.discovery.categories.program|event`, comma-separated codes); switching sides brings back that side's choice.
    private(set) var categories = ActivityViewModel.storedCategories(ActivityViewModel.storedKind())
    private static func categoriesKey(_ kind: DiscoveryKind) -> String { "dearby.discovery.categories.\(kind.rawValue)" }
    private static func storedCategories(_ kind: DiscoveryKind) -> Set<ActivityCategory> {
        let codes = (UserDefaults.standard.string(forKey: categoriesKey(kind)) ?? "").split(separator: ",")
        return Set(codes.compactMap { ActivityCategory(rawValue: String($0)) }.filter(kind.categories.contains))
    }
    /// Applies a 종류 choice (this side's 종류 only) and reloads the list; true when it changed.
    @discardableResult func setCategories(_ chosen: Set<ActivityCategory>) -> Bool {
        let chosen = chosen.intersection(kind.categories)
        guard chosen != categories else { return false }
        categories = chosen
        UserDefaults.standard.set(categoryList.map(\.rawValue).joined(separator: ","), forKey: Self.categoriesKey(kind))
        start(query ?? "")
        return true
    }
    /// The chosen 종류 in contract order, as the requests send them.
    var categoryList: [ActivityCategory] { ActivityCategory.allCases.filter(categories.contains) }
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
    /// Activities in 내 활동, keyed by activity id.
    private(set) var appliedIDs: Set<String> = []
    /// The user's stage marks (contract "내 활동 진행 트랙"): activity id → schedule id → mark.
    private(set) var stageMarks: [String: [String: StageMark]] = [:]
    /// Server records whose activity answers 404 (contract "삭제된 활동"): listed apart with only 빼기, in id order.
    private(set) var deletedIDs: [String] = []
    /// True while the signed-in list loads and nothing is shown yet.
    private(set) var mineLoading = false
    /// A one-shot message after a 내 활동 change could not be saved; cleared with `clearNotice()`.
    private(set) var notice: String?
    /// The signed-in account's 내 활동 calls; nil while signed out.
    private var server: MineServer?
    /// The marks above mirror the server (the device records were imported or there were none).
    private var synced = false
    /// Contract #148 "신청 확인": after an official application link opened, ask once when the app comes back.
    enum ApplyAnswer { case applied, notYet, neverAsk }
    private(set) var asking: ActivityModel?
    private var opened: String?
    private var leftApp = false
    private var neverAsk: Set<String> = []
    /// `내 활동으로` on a detail (contract "활동 상세 내 활동 행동 정리"): the activity the 내 활동 tab should open next; the tab clears it once pushed.
    var showInMine: String?
    /// An `/activities/<id>` universal link (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): the activity the 발견 tab should push
    /// next; the tab clears it once pushed.
    var showInDiscovery: String?
    @ObservationIgnored private var linkSources: [String: String] = [:]
    /// A tapped 세션 이동 알림 (contract "찜한 세션 이동 알림"): the block whose timetable the opened detail shows on 찜한 세션, taken once.
    @ObservationIgnored private var linkTimetables: [String: String] = [:]
    /// Every activity seen this session, so details and applied marks outlive a new search.
    private var seen: [String: ActivityModel] = [:]
    private var cursor: Int64?
    var hasMore: Bool { cursor != nil }
    private var firstPage: Task<Void, Never>?
    private var nextPage: Task<Void, Never>?
    /// In-flight activity loads with the groups each asked for.
    private var programLoads: [Int64: (groups: Set<StatusGroup>, categories: Set<ActivityCategory>, task: Task<Void, Never>)] = [:]
    private let fetchPrograms: @Sendable (_ query: String, _ cursor: Int64?, _ status: [ActivityStatus], _ kind: DiscoveryKind,
                                          _ categories: [ActivityCategory]) async throws -> ProgramPage
    /// One program's activities in the given states and 종류 (none: every 종류).
    private let fetchActivities: @Sendable (_ programID: Int64, _ status: [ActivityStatus], _ kind: DiscoveryKind,
                                            _ categories: [ActivityCategory]) async throws -> ActivityPage
    /// `GET /v1/activities/:id` for 내 활동 records and linked activities not seen in discovery; nil when the activity is gone (404).
    private let fetchActivity: @Sendable (_ id: String) async throws -> ActivityModel?
    /// One block's 트랙·세션 시간표; nil on 404.
    private let fetchTimetable: @Sendable (_ activityID: String, _ scheduleID: String) async throws -> Timetable?
    init(programs: @escaping @Sendable (_ query: String, _ cursor: Int64?, _ status: [ActivityStatus], _ kind: DiscoveryKind,
                                        _ categories: [ActivityCategory]) async throws -> ProgramPage,
         activities: @escaping @Sendable (_ programID: Int64, _ status: [ActivityStatus], _ kind: DiscoveryKind,
                                          _ categories: [ActivityCategory]) async throws -> ActivityPage,
         activity: @escaping @Sendable (_ id: String) async throws -> ActivityModel?,
         timetable: @escaping @Sendable (_ activityID: String, _ scheduleID: String) async throws -> Timetable?,
         webOrigin: URL = URL(string: AppOrigins.defaultWeb)!) {
        self.webOrigin = webOrigin
        fetchPrograms = programs
        fetchActivities = activities
        fetchActivity = activity
        fetchTimetable = timetable
    }
    /// 트랙·세션 시간표 (contract "트랙·세션 시간표" 2026-10-26) of one block; nil when the server has none (404).
    func timetable(_ activityID: String, _ scheduleID: String) async throws -> Timetable? { try await fetchTimetable(activityID, scheduleID) }

    func activity(_ id: String) -> ActivityModel? { seen[id] }
    /// An activity shown outside the program list (추천), so its detail can open.
    func remember(_ activity: ActivityModel) { seen[activity.id] = activity }
    /// Opens a linked activity in 발견, loading it first when discovery has not shown it. A 404 still opens the detail, which says the
    /// activity is gone; any other failure says so and stays put.
    /// `source` (`link` or `push`) is the 사용 통계 `activity_view` source; the detail it opens reads it once (`takeLinkSource`).
    func openLinked(_ id: String, source: String) async {
        if await prepare(id) {
            linkSources[id] = source
            showInDiscovery = id
        }
    }
    func takeLinkSource(_ id: String) -> String? { linkSources.removeValue(forKey: id) }
    /// Opens a linked activity with its block's timetable sheet on 찜한 세션 (a tapped 세션 이동 알림).
    func openLinkedTimetable(_ id: String, scheduleID: String) async {
        guard await prepare(id) else { return }
        linkTimetables[id] = scheduleID
        await openLinked(id, source: "push")
    }
    func takeLinkTimetable(_ id: String) -> String? { linkTimetables.removeValue(forKey: id) }
    /// Loads an activity discovery has not shown so its detail can open (알림 목록 rows); false, with `notice`, when it could not be loaded.
    /// A 404 is still true: the detail says the activity is gone.
    func prepare(_ id: String) async -> Bool {
        guard seen[id] == nil else { return true }
        do {
            if let activity = try await fetchActivity(id) { seen[id] = activity }
            return true
        } catch {
            notice = "활동을 불러오지 못했어요. 연결을 확인한 뒤 다시 시도해 주세요."
            return false
        }
    }
    /// The build's web origin (`DearbyWebOrigin`), for activity links written into calendar events.
    let webOrigin: URL
    func calendarLink(_ id: String) -> URL { ActivityModel.calendarLink(id, web: webOrigin) }
    /// Applied activities with their tracks, nearest next stage first; date-less and ended tracks last, then by id.
    func appliedProgress(now: Date) -> [AppliedActivity] {
        appliedIDs.compactMap { seen[$0] }.map { AppliedActivity(activity: $0, progress: progress($0, now: now)) }
            .sorted { ($0.progress?.nextStart ?? .distantFuture, $0.id) < ($1.progress?.nextStart ?? .distantFuture, $1.id) }
    }
    func progress(_ activity: ActivityModel, now: Date) -> ActivityProgress? {
        ActivityStages.build(activity, marks: stageMarks[activity.id] ?? [:], now: now)
    }
    func markStage(_ activityID: String, _ scheduleID: String, _ mark: StageMark) {
        stageMarks[activityID, default: [:]][scheduleID] = mark
        guard let server, synced, let activity = Int64(activityID), let schedule = Int64(scheduleID) else { return }
        save { _ = try await server.putStatus(activity, schedule, mark.statusID, mark.kind.rawValue) }
    }
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
        programLoads.values.forEach { $0.task.cancel() }
        programLoads = [:]
        loadingMore = false
        moreFailed = false
        refreshFailed = false
        let task = Task {
            do {
                let page = try await fetchPrograms(query, nil, Self.statuses(statusGroups), kind, categoryList)
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
                loadedActivities = loadedActivities.filter { shown.contains($0.key) }
                // Programs `append` just started loading are skipped by the in-flight guard.
                for id in expanded { loadActivities(id, groups: statusGroups, categories: categories, reload: true) }
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
                let page = try await fetchPrograms(query, cursor, Self.statuses(statusGroups), kind, categoryList)
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
        loadActivities(programID, groups: statusGroups, categories: categories)
    }
    func retryActivities(_ programID: Int64, groups: Set<StatusGroup>, categories: Set<ActivityCategory>) {
        loadActivities(programID, groups: groups, categories: categories)
    }
    /// A program page (from 즐겨찾기) shows every state and 종류: loads them unless they are loaded or loading.
    func loadProgram(_ programID: Int64) { loadActivities(programID, groups: Set(StatusGroup.allCases), categories: []) }
    /// The program's loaded activities when they cover `groups` and were read with the same 종류 (a program page loads every state and
    /// 종류, so a discovery card without a 종류 choice can reuse them).
    func activities(_ programID: Int64, for groups: Set<StatusGroup>, categories: Set<ActivityCategory> = []) -> ProgramActivities? {
        loadedActivities[programID].flatMap { $0.groups.isSuperset(of: groups) && $0.categories == categories ? $0 : nil }
    }
    /// The `+` beside an activity's heart (contract "`+` 내 활동에 추가" 2026-10-25): adds it, or once added opens it in 내 활동.
    /// `source` is the 사용 통계 `myactivity_add` source (`discovery`, `queue`, `favorites`, …).
    func addToMine(_ id: String, source: String) {
        if appliedIDs.contains(id) { showInMine = id } else {
            AppAnalytics.event("myactivity_add", AppAnalytics.activity(id, seen[id]).merging(["source": source]) { $1 })
            apply(id, true)
        }
    }
    private func start(_ query: String) {
        firstPage?.cancel()
        nextPage?.cancel()
        nextPage = nil
        programLoads.values.forEach { $0.task.cancel() }
        programLoads = [:]
        self.query = query
        phase = .loading
        programs = []
        expanded = []
        loadedActivities = [:]
        loadingPrograms = []
        failedPrograms = []
        cursor = nil
        loadingMore = false
        moreFailed = false
        firstPage = Task {
            do {
                let page = try await fetchPrograms(query, nil, Self.statuses(statusGroups), kind, categoryList)
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
    }
    /// Loads the program's activities in `groups`' states unless loaded or loading ones cover them; `reload` fetches again even then
    /// (pull to refresh keeps the old ones on screen until the answer lands). A load for fewer groups in flight gives way.
    private func loadActivities(_ programID: Int64, groups: Set<StatusGroup>, categories: Set<ActivityCategory>, reload: Bool = false) {
        if !reload, activities(programID, for: groups, categories: categories) != nil { return }
        if let inFlight = programLoads[programID] {
            if inFlight.groups.isSuperset(of: groups) && inFlight.categories == categories { return }
            inFlight.task.cancel()
        }
        loadingPrograms.insert(programID)
        failedPrograms.remove(programID)
        let task = Task {
            defer { if !Task.isCancelled { loadingPrograms.remove(programID); programLoads[programID] = nil } }
            do {
                let page = try await fetchActivities(programID, Self.statuses(groups), kind, ActivityCategory.allCases.filter(categories.contains))
                guard !Task.isCancelled else { return }
                for item in page.items { seen[item.id] = item }
                loadedActivities[programID] = ProgramActivities(groups: groups, categories: categories, items: page.items, now: loadedAt)
            } catch {
                if !Task.isCancelled { failedPrograms.insert(programID) }
            }
        }
        programLoads[programID] = (groups, categories, task)
    }
    func apply(_ id: String, _ value: Bool) {
        if value { appliedIDs.insert(id) } else { appliedIDs.remove(id); stageMarks[id] = nil; deletedIDs.removeAll { $0 == id } }
        guard let server, synced, let activity = Int64(id) else { return }
        save { if value { _ = try await server.put(activity) } else { try await server.delete(activity) } }
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

    /// The account signed in (or the app started signed in): from now on 내 활동 is the server's.
    func signedIn(_ server: MineServer) async {
        self.server = server
        await syncMine()
    }
    /// The account signed out: the server's records leave with it; marks made signed out stay in memory.
    func signedOut() {
        server = nil
        mineLoading = false
        guard synced else { return }
        synced = false
        appliedIDs = []
        stageMarks = [:]
        deletedIDs = []
    }
    /// Loads the server's 내 활동 (pull to refresh, a failed change). Before the first success the records kept signed out are
    /// uploaded with `import` instead and then replaced by its answer; a failed import keeps them here for the next try.
    func syncMine() async {
        guard let server else { return }
        let local = synced ? [] : importItems()
        if !synced && appliedIDs.isEmpty { mineLoading = true }
        defer { mineLoading = false }
        do {
            let items = local.isEmpty ? try await server.list() : try await server.importItems(local)
            guard self.server != nil else { return }
            replace(items)
            await resolve(items.map { String($0.activityId) })
        } catch {
            notice = Self.message(error, saving: !local.isEmpty)
        }
    }
    func clearNotice() { notice = nil }
    private func importItems() -> [AccountClient.MyActivityImport] {
        appliedIDs.sorted().compactMap { id in
            guard let activityID = Int64(id) else { return nil }
            // Import carries default kinds only (contract): waiting is the default anyway, and OTHER has no default status.
            let statuses = (stageMarks[id] ?? [:]).compactMap { schedule, mark in
                [.passed, .failed].contains(mark.kind)
                    ? Int64(schedule).map { AccountClient.MyActivityImport.Status(scheduleId: $0, kind: mark.kind.rawValue) } : nil
            }
            return AccountClient.MyActivityImport(activityId: activityID, statuses: statuses)
        }
    }
    private func replace(_ items: [AccountClient.MyActivity]) {
        synced = true
        appliedIDs = Set(items.map { String($0.activityId) })
        stageMarks = Dictionary(items.map { item in
            (String(item.activityId), Dictionary(item.statuses.map { status in
                (String(status.scheduleId), StageMark(statusID: status.statusId, kind: ScheduleStatus.Kind(rawValue: status.kind) ?? .other))
            }, uniquingKeysWith: { $1 }))
        }, uniquingKeysWith: { $1 })
        deletedIDs = deletedIDs.filter(appliedIDs.contains)
    }
    /// Loads the activities discovery has not shown; a 404 marks the record "삭제된 활동", any other failure leaves it for the next load.
    private func resolve(_ ids: [String]) async {
        await withTaskGroup(of: (String, Result<ActivityModel?, Error>).self) { group in
            for id in ids where seen[id] == nil {
                group.addTask { [fetchActivity] in
                    do { return (id, .success(try await fetchActivity(id))) } catch { return (id, .failure(error)) }
                }
            }
            for await (id, result) in group {
                switch result {
                case .success(let activity?): seen[id] = activity; deletedIDs.removeAll { $0 == id }
                case .success(nil): if !deletedIDs.contains(id) { deletedIDs.append(id) }
                case .failure: break
                }
            }
        }
        deletedIDs.sort { (Int64($0) ?? 0) < (Int64($1) ?? 0) }
    }
    /// Sends one change; on failure says so and reloads the server's list, which undoes the change on screen.
    private func save(_ call: @escaping @MainActor () async throws -> Void) {
        Task {
            do { try await call() } catch {
                notice = Self.message(error, saving: true)
                await syncMine()
            }
        }
    }
    private static func message(_ error: Error, saving: Bool) -> String {
        switch error as? AccountError {
        case .unauthorized: "로그인이 만료됐어요. 다시 로그인해 주세요."
        case .consentRequired: "바뀐 개인정보 처리방침에 동의해야 내 활동을 저장할 수 있어요. 프로필 탭에서 동의해 주세요."
        default: saving ? "내 활동을 저장하지 못했어요. 잠시 후 다시 시도해 주세요." : "내 활동을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
        }
    }
}

/// The signed-in account's 내 활동 calls (contract "내 활동 서버 저장" 2026-10-22), built by the app from `AccountClient` with the
/// account's token refresh.
struct MineServer: Sendable {
    let list: @Sendable () async throws -> [AccountClient.MyActivity]
    /// Adds the activity.
    let put: @Sendable (_ activityID: Int64) async throws -> AccountClient.MyActivity
    let delete: @Sendable (_ activityID: Int64) async throws -> Void
    let putStatus: @Sendable (_ activityID: Int64, _ scheduleID: Int64, _ statusID: Int64?, _ kind: String) async throws -> AccountClient.MyActivity
    let importItems: @Sendable (_ items: [AccountClient.MyActivityImport]) async throws -> [AccountClient.MyActivity]
}

/// One program's activities by 활동 상태 (contract "활동 상태" 2026-10-26), each list in display order: 모집 중 (the page groups them by
/// 활동 종류), 모집 예정 (nearest recruitment start first), 진행 중 and 지난 활동 (모집 마감·모집 정보 없음·종료 whose activity falls within the
/// last three years — the server's `status` has no window here; an undated one stays), both latest activity start first. `groups` are the ones it was loaded for; an
/// activity outside them (an older server ignores `status`) is left out.
struct ProgramActivities {
    let groups: Set<StatusGroup>
    /// The 종류 they were read with; empty is every 종류.
    let categories: Set<ActivityCategory>
    let recruiting: [ActivityModel]
    let upcoming: [ActivityModel]
    let inProgress: [ActivityModel]
    let past: [ActivityModel]
    init(groups: Set<StatusGroup>, categories: Set<ActivityCategory>, items: [ActivityModel], now: Date) {
        self.groups = groups
        self.categories = categories
        let since = Calendar(identifier: .gregorian).date(byAdding: .year, value: -3, to: now) ?? now
        var byStatus: [ActivityStatus: [ActivityModel]] = [:]
        for item in items {
            guard let status = item.status(at: now), groups.contains(StatusGroup(status)) else { continue }
            byStatus[status, default: []].append(item)
        }
        func latestFirst(_ list: [ActivityModel]) -> [ActivityModel] {
            list.sorted { ($0.activityStage?.anchor ?? .distantPast, $0.title) > ($1.activityStage?.anchor ?? .distantPast, $1.title) }
        }
        recruiting = byStatus[.recruiting] ?? []
        upcoming = (byStatus[.recruitingUpcoming] ?? [])
            .sorted { ($0.upcomingStart ?? .distantFuture, $0.title) < ($1.upcomingStart ?? .distantFuture, $1.title) }
        inProgress = latestFirst(byStatus[.inProgress] ?? [])
        past = latestFirst(StatusGroup.past.statuses.flatMap { byStatus[$0] ?? [] }.filter { $0.activityStage?.anchor.map { $0 >= since } ?? true })
    }
}
