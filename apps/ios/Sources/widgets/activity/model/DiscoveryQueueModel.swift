import Foundation
import Observation

/// 맞춤 대기열 (contract "맞춤 대기열(스토리형 쇼츠)·조직 친화도·활동 벡터" 2026-10-24): today's cards and the reactions to them.
/// Signed in, the server builds the queue (`GET /v1/me/discovery-queue`, `더 받기` refills it) and every reaction goes to it. Signed out, the
/// 추천 results stand in and reactions stay on the device (`localKey`, a new key; nothing is uploaded on sign-in). Only a 404 hides the entry.
/// The ⋯ choices take the card out at once but are sent only when their `되돌리기` notice ends (the API has no undo), or when the screen closes.
@MainActor @Observable final class DiscoveryQueueModel {
    enum Choice: CaseIterable, Identifiable {
        case dismiss, lessField, hideProgram, ineligible
        var id: Self { self }
        var label: String {
            switch self {
            case .dismiss: "관심 없어요"
            case .lessField: "이 분야는 덜 보기"
            case .hideProgram: "이 프로그램 숨기기"
            case .ineligible: "지원 자격이 안 돼요"
            }
        }
        /// The `되돌리기` notice.
        var notice: String {
            switch self {
            case .dismiss: "관심 없는 활동으로 표시했어요"
            case .lessField: "이 분야는 덜 보여 드릴게요"
            case .hideProgram: "이 프로그램을 숨겼어요"
            case .ineligible: "지원 자격이 안 되는 활동으로 표시했어요"
            }
        }
        var signal: RecommendationSignal {
            switch self {
            case .dismiss: .dismiss
            case .lessField: .lessField
            case .hideProgram: .hideProgram
            case .ineligible: .ineligible
            }
        }
    }
    /// A ⋯ choice waiting for its notice to end: the cards it took out, at their places, so `되돌리기` can put them back.
    struct Pending {
        let choice: Choice
        let item: RecommendedActivity
        let removed: [Int: RecommendedActivity]
    }
    private(set) var items: [RecommendedActivity] = []
    private(set) var liked: Set<String> = []
    private(set) var loading = false
    /// The last load failed (connection); the screen offers to try again.
    private(set) var failed = false
    private(set) var pending: Pending?
    /// Signed in: the server answered the queue API (not 404). Signed out the 추천 results decide (`showsEntry`).
    private(set) var available = false
    static let localKey = "dearby.discoveryQueue.local"
    private var server: DiscoveryQueueServer?
    private let candidates: @Sendable (AccountClient.RecommendationPreferences) async throws -> RecommendationPage?
    private let defaults: UserDefaults
    private var local: LocalSignals
    /// Signed out: cards already shown this run, so `더 받기` gives others, and the answers the stand-in was built from.
    private var shown: Set<String> = []
    private var builtFor: AccountClient.RecommendationPreferences?
    /// `COMPLETE`/`SKIP_EARLY`/`OPEN_DETAIL` once per card this run.
    private var sent: Set<String> = []
    init(candidates: @escaping @Sendable (AccountClient.RecommendationPreferences) async throws -> RecommendationPage?, defaults: UserDefaults = .standard) {
        self.candidates = candidates
        self.defaults = defaults
        local = defaults.data(forKey: Self.localKey).flatMap { try? JSONDecoder().decode(LocalSignals.self, from: $0) } ?? LocalSignals()
    }

    /// The entry under 추천: signed in when the server has the queue, signed out when there is something to recommend.
    func showsEntry(hasRecommendations: Bool) -> Bool { server != nil ? available : hasRecommendations }
    func signedIn(_ server: DiscoveryQueueServer) async {
        self.server = server
        reset()
        await load()
    }
    func signedOut() {
        server = nil
        available = false
        reset()
    }
    /// The screen opened: signed in the server's queue is read every time; signed out the stand-in is kept until `더 받기` or new answers.
    func open(_ preferences: AccountClient.RecommendationPreferences?) async {
        if server != nil || items.isEmpty || builtFor != preferences { await load(preferences) }
    }
    /// Today's queue: the server's, or (signed out) the first 10 of the 추천 results not reacted to or shown already.
    /// A failure other than 404 keeps what is shown.
    func load(_ preferences: AccountClient.RecommendationPreferences? = nil) async {
        loading = true
        defer { loading = false }
        do {
            if let server {
                let loaded = try await server.load()
                guard self.server != nil else { return }
                available = loaded != nil
                items = loaded ?? []
            } else {
                let page = try await candidates(preferences ?? .init())
                items = signedOutQueue(page?.items ?? [])
                shown.formUnion(items.map(\.id))
                builtFor = preferences
            }
            failed = false
        } catch { failed = true }
    }
    /// `더 받기`: signed in the server rebuilds the queue from the remaining candidates; signed out the next 추천 results.
    func refill(_ preferences: AccountClient.RecommendationPreferences?) async {
        if let server {
            loading = true
            do { try await server.refill() } catch {
                loading = false
                failed = true
                return
            }
        }
        await load(preferences)
    }
    func toggleLike(_ item: RecommendedActivity) {
        setLike(item, !liked.contains(item.id))
    }
    /// Double tap: 관심 있어요 only (a second double tap keeps it).
    func like(_ item: RecommendedActivity) {
        if !liked.contains(item.id) { setLike(item, true) }
    }
    private func setLike(_ item: RecommendedActivity, _ on: Bool) {
        if on { liked.insert(item.id) } else { liked.remove(item.id) }
        if server != nil {
            send(on ? .like : .unlike, item)
        } else {
            if on { local.liked.insert(item.id) } else { local.liked.remove(item.id) }
            save()
        }
    }
    /// Once per card: `COMPLETE` (all three scenes), `SKIP_EARLY` (left on the first scene), `OPEN_DETAIL` (자세히).
    func viewed(_ item: RecommendedActivity, _ kind: RecommendationSignal) {
        guard server != nil, sent.insert("\(item.id)|\(kind.rawValue)").inserted else { return }
        send(kind, item)
    }
    /// A ⋯ choice: the card (with `이 프로그램 숨기기`, every card of that program) leaves the queue now; it is recorded when the notice ends.
    func choose(_ choice: Choice, _ item: RecommendedActivity) {
        commitPending()
        let removed = Dictionary(uniqueKeysWithValues: items.enumerated().filter { _, other in
            other.id == item.id || (choice == .hideProgram && item.programID != nil && other.programID == item.programID)
        }.map { ($0.offset, $0.element) })
        items.removeAll { other in removed.values.contains { $0.id == other.id } }
        pending = Pending(choice: choice, item: item, removed: removed)
    }
    func undo() {
        guard let pending else { return }
        self.pending = nil
        for (index, item) in pending.removed.sorted(by: { $0.key < $1.key }) { items.insert(item, at: min(index, items.count)) }
    }
    /// The notice ended or the screen closed: the pending choice is recorded (server signal, or the device list signed out).
    func commitPending() {
        guard let pending else { return }
        self.pending = nil
        let item = pending.item
        if server != nil {
            send(pending.choice.signal, item, tagID: pending.choice == .lessField ? item.activity.fieldTags.first?.id : nil)
            return
        }
        switch pending.choice {
        case .dismiss, .ineligible: local.excluded.insert(item.id)
        case .hideProgram: if let program = item.programID { local.hiddenPrograms.insert(program) } else { local.excluded.insert(item.id) }
        case .lessField: if let tag = item.activity.fieldTags.first?.id { local.lessFields.insert(tag) }
        }
        save()
    }
    private func send(_ kind: RecommendationSignal, _ item: RecommendedActivity, tagID: Int64? = nil) {
        guard let server else { return }
        // Best effort: a lost reaction only weakens tomorrow's queue.
        Task { try? await server.signal(item.activity.id, kind, tagID) }
    }
    /// Signed out: without reacted-to cards and hidden programs, 덜 보기 fields last, at most two per program, ten in all.
    private func signedOutQueue(_ all: [RecommendedActivity]) -> [RecommendedActivity] {
        let kept = all.filter { item in
            !local.excluded.contains(item.id) && item.programID.map(local.hiddenPrograms.contains) != true && !shown.contains(item.id)
        }
        let lessened = { (item: RecommendedActivity) in item.activity.fieldTags.contains { self.local.lessFields.contains($0.id) } }
        var perProgram: [Int64: Int] = [:]
        return (kept.filter { !lessened($0) } + kept.filter(lessened)).filter { item in
            guard let program = item.programID else { return true }
            perProgram[program, default: 0] += 1
            return perProgram[program, default: 0] <= 2
        }.prefix(10).map { $0 }
    }
    private func reset() {
        items = []
        pending = nil
        failed = false
        shown = []
        sent = []
        liked = server == nil ? local.liked : []
    }
    private func save() {
        if let data = try? JSONEncoder().encode(local) { defaults.set(data, forKey: Self.localKey) }
    }
    /// Signed-out reactions on this device.
    private struct LocalSignals: Codable {
        var liked: Set<String> = []
        var excluded: Set<String> = []
        var hiddenPrograms: Set<Int64> = []
        var lessFields: Set<Int64> = []
    }
}

/// The signed-in account's 맞춤 대기열 calls, built by the app with the account's token refresh.
struct DiscoveryQueueServer: Sendable {
    /// Nil when the server has no queue API (404).
    let load: @Sendable () async throws -> [RecommendedActivity]?
    let refill: @Sendable () async throws -> Void
    let signal: @Sendable (String, RecommendationSignal, Int64?) async throws -> Void
}
