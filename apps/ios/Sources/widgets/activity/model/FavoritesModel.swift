import Foundation
import Observation

/// 즐겨찾기 (contract "즐겨찾기(하트)·새 모집 푸시 알림" 2026-10-25): signed in only; signed out a heart asks for sign-in (`signInRequested`).
/// Every heart in the app reads `levels`, built from `GET /v1/me/favorites` (discovery responses are read without a token, so they carry
/// no `favorited`). A heart or a level changes on screen at once; a failed call (also a 404 from a server without the API) puts it back
/// and says so in `notice`. The queue's ♥ is this same favorite (the server records the LIKE/UNLIKE signal). A swipe in the favorites
/// screen hides the row with a `되돌리기` notice and is sent only when the notice ends.
@MainActor @Observable final class FavoritesModel {
    enum Phase: Equatable { case signedOut, loading, loaded, failed, unavailable }
    /// A swiped-away favorite waiting for its notice to end, with its place so `되돌리기` can put it back.
    struct Pending {
        let target: FavoriteTarget
        let level: NotifyLevel
        let program: (index: Int, item: FavoriteProgram)?
        let activity: (index: Int, item: FavoriteActivity)?
    }
    private(set) var phase = Phase.signedOut
    private(set) var programs: [FavoriteProgram] = []
    private(set) var activities: [FavoriteActivity] = []
    /// Every favorite and its 알림 수준.
    private(set) var levels: [FavoriteTarget: NotifyLevel] = [:]
    private(set) var pending: Pending?
    /// A one-shot message after a change could not be saved.
    var notice: String?
    /// A heart was pressed signed out: the app opens sign-in and clears it.
    var signInRequested = false
    /// A program was just favorited: the app asks for notification permission the first time (contract "푸시 알림").
    @ObservationIgnored var programFavorited: () -> Void = {}
    /// 사용 통계 `field`/`category` of a target, from what the app has loaded (set by the app).
    @ObservationIgnored var describe: (FavoriteTarget) -> [String: Any] = { _ in [:] }
    private var server: FavoritesServer?

    func isFavorite(_ target: FavoriteTarget) -> Bool { levels[target] != nil }
    func signedIn(_ server: FavoritesServer) async {
        self.server = server
        await load()
    }
    func signedOut() {
        server = nil
        phase = .signedOut
        programs = []
        activities = []
        levels = [:]
        pending = nil
    }
    func load() async {
        guard let server else { return }
        if programs.isEmpty && activities.isEmpty && phase != .loaded { phase = .loading }
        do {
            let list = try await server.load()
            guard self.server != nil else { return }
            programs = list?.programs ?? []
            activities = list?.activities ?? []
            levels = Dictionary(programs.map { (FavoriteTarget.program($0.id), $0.level) } + activities.map { (.activity($0.id), $0.level) },
                                uniquingKeysWith: { first, _ in first })
            if let pending { drop(pending.target) }
            phase = list == nil ? .unavailable : .loaded
        } catch {
            if phase == .loading { phase = .failed }
        }
    }
    /// The heart: on with the default level, or off.
    func toggle(_ target: FavoriteTarget) {
        guard let server else { signInRequested = true; return }
        commitPending()
        let before = levels[target]
        log(before == nil ? "favorite_add" : "favorite_remove", target)
        if before == nil {
            levels[target] = .application
            if case .program = target { programFavorited() }
        } else {
            drop(target)
        }
        send(failure: before == nil ? "즐겨찾기에 넣지 못했어요. 잠시 후 다시 시도해 주세요." : "즐겨찾기를 해제하지 못했어요. 잠시 후 다시 시도해 주세요.",
             revert: { $0.levels[target] = before }) {
            if before == nil { try await server.put(target, nil) } else { try await server.delete(target) }
        }
    }
    /// The queue's double tap: favorite only, never off.
    func add(_ target: FavoriteTarget) { if !isFavorite(target) { toggle(target) } }
    /// The bell sheet's choice.
    func setLevel(_ target: FavoriteTarget, _ level: NotifyLevel) {
        guard let server, let before = levels[target], before != level else { return }
        setStoredLevel(target, level)
        AppAnalytics.event("notify_level_set", ["type": Self.type(target), "level": level.rawValue.lowercased()])
        send(failure: "알림 설정을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.", revert: { $0.setStoredLevel(target, before) }) {
            try await server.put(target, level)
        }
    }
    /// The favorites screen opened a program with `새 모집`: the badge goes.
    func seen(_ programID: Int64) {
        guard let server, let index = programs.firstIndex(where: { $0.id == programID }), programs[index].newRecruitment else { return }
        programs[index].newRecruitment = false
        // Best effort: the badge comes back on the next load if this is lost.
        Task { try? await server.seen(programID) }
    }
    /// A swipe in the favorites screen: the row goes now and is removed when the `되돌리기` notice ends.
    func removeWithUndo(_ target: FavoriteTarget) {
        commitPending()
        guard let level = levels[target] else { return }
        let program = programs.firstIndex { FavoriteTarget.program($0.id) == target }.map { (index: $0, item: programs[$0]) }
        let activity = activities.firstIndex { FavoriteTarget.activity($0.id) == target }.map { (index: $0, item: activities[$0]) }
        drop(target)
        pending = Pending(target: target, level: level, program: program, activity: activity)
    }
    func undo() {
        guard let pending else { return }
        self.pending = nil
        levels[pending.target] = pending.level
        if let program = pending.program { programs.insert(program.item, at: min(program.index, programs.count)) }
        if let activity = pending.activity { activities.insert(activity.item, at: min(activity.index, activities.count)) }
    }
    /// The notice ended, another change started or the screen closed: the pending removal is sent.
    func commitPending() {
        guard let pending, let server else { return }
        self.pending = nil
        let target = pending.target
        log("favorite_remove", target)
        send(failure: "즐겨찾기를 해제하지 못했어요. 잠시 후 다시 시도해 주세요.", revert: { _ in }) { try await server.delete(target) }
    }
    /// 사용 통계 `favorite_add`/`favorite_remove`: the target's type, id and tags.
    private func log(_ event: String, _ target: FavoriteTarget) {
        let id = switch target {
        case .program(let id): String(id)
        case .activity(let id): id
        }
        AppAnalytics.event(event, describe(target).merging(["type": Self.type(target), "id": id]) { $1 })
    }
    private static func type(_ target: FavoriteTarget) -> String {
        switch target {
        case .program: "program"
        case .activity: "activity"
        }
    }
    private func drop(_ target: FavoriteTarget) {
        levels[target] = nil
        programs.removeAll { FavoriteTarget.program($0.id) == target }
        activities.removeAll { FavoriteTarget.activity($0.id) == target }
    }
    private func setStoredLevel(_ target: FavoriteTarget, _ level: NotifyLevel) {
        levels[target] = level
        if let index = programs.firstIndex(where: { FavoriteTarget.program($0.id) == target }) { programs[index].level = level }
        if let index = activities.firstIndex(where: { FavoriteTarget.activity($0.id) == target }) { activities[index].level = level }
    }
    /// Sends one change, then reloads the list (new favorites take their server place); on failure puts the screen back and says so.
    private func send(failure: String, revert: @escaping @MainActor (FavoritesModel) -> Void, _ call: @escaping @MainActor () async throws -> Void) {
        Task {
            do { try await call() } catch {
                guard server != nil else { return }
                revert(self)
                notice = (error as? AccountError) == .unauthorized ? "로그인이 만료됐어요. 다시 로그인해 주세요." : failure
            }
            await load()
        }
    }
}

/// The signed-in account's favorites calls, built by the app with the account's token refresh.
struct FavoritesServer: Sendable {
    /// Nil when the server has no favorites API (404).
    let load: @Sendable () async throws -> FavoriteList?
    /// Adds the favorite (nil: the default level) or changes its level.
    let put: @Sendable (FavoriteTarget, NotifyLevel?) async throws -> Void
    let delete: @Sendable (FavoriteTarget) async throws -> Void
    let seen: @Sendable (Int64) async throws -> Void
}
