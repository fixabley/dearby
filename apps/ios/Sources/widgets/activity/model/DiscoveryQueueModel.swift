import Foundation
import Observation

/// 맞춤 대기열 (contract "맞춤 대기열(스토리형 쇼츠)·조직 친화도·활동 벡터" 2026-10-24): today's cards and the reactions to them.
/// Signed in only (contract "비로그인에서 만들기·추천을 막음" 2026-10-25): the server builds the queue (`GET /v1/me/discovery-queue`, `더 받기`
/// refills it) and every reaction goes to it. Reactions an earlier build kept on the device (`dearby.discoveryQueue.local`) are neither read nor removed.
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
    private var server: DiscoveryQueueServer?
    /// `COMPLETE`/`SKIP_EARLY`/`OPEN_DETAIL` once per card this run.
    private var sent: Set<String> = []

    func signedIn(_ server: DiscoveryQueueServer) async {
        self.server = server
        reset()
        await load()
    }
    func signedOut() {
        server = nil
        reset()
    }
    /// Today's queue, read every time the screen opens. A failure other than 404 keeps what is shown.
    func load() async {
        guard let server else { return }
        loading = true
        defer { loading = false }
        do {
            let loaded = try await server.load()
            guard self.server != nil else { return }
            items = loaded ?? []
            failed = false
        } catch { failed = true }
    }
    /// `더 받기`: the server rebuilds the queue from the remaining candidates.
    func refill() async {
        guard let server else { return }
        loading = true
        do { try await server.refill() } catch {
            loading = false
            failed = true
            return
        }
        await load()
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
        send(on ? .like : .unlike, item)
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
    /// The notice ended or the screen closed: the pending choice is sent.
    func commitPending() {
        guard let pending else { return }
        self.pending = nil
        let item = pending.item
        send(pending.choice.signal, item, tagID: pending.choice == .lessField ? item.activity.fieldTags.first?.id : nil)
    }
    private func send(_ kind: RecommendationSignal, _ item: RecommendedActivity, tagID: Int64? = nil) {
        guard let server else { return }
        // Best effort: a lost reaction only weakens tomorrow's queue.
        Task { try? await server.signal(item.activity.id, kind, tagID) }
    }
    private func reset() {
        items = []
        pending = nil
        failed = false
        sent = []
        liked = []
    }
}

/// The signed-in account's 맞춤 대기열 calls, built by the app with the account's token refresh.
struct DiscoveryQueueServer: Sendable {
    /// Nil when the server has no queue API (404).
    let load: @Sendable () async throws -> [RecommendedActivity]?
    let refill: @Sendable () async throws -> Void
    let signal: @Sendable (String, RecommendationSignal, Int64?) async throws -> Void
}
