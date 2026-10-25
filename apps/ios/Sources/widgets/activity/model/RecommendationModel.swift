import Foundation
import Observation

/// 추천 (contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24): the funnel's answers and its result preview, signed in only
/// (contract "비로그인에서 만들기·추천을 막음" 2026-10-25). `signedIn(_:)` reads the account's answers; signed out there are none and the
/// funnel never shows. Answers an earlier build kept on the device (`dearby.recommendation.*`) are neither read nor removed.
/// The recommendation API only gives the funnel result's count; a 404 reads as none.
@MainActor @Observable final class RecommendationModel {
    typealias Preferences = AccountClient.RecommendationPreferences
    /// The answers in use; nil until they are read, or when there are none yet.
    private(set) var preferences: Preferences? { didSet { publishInterests() } }
    /// The account's answers have been read, so the funnel may decide whether to show.
    private(set) var ready = false
    /// The server's count of matching activities when it sends `total`; else the items received.
    private(set) var total = 0
    private(set) var tags: [TagOption] = []
    /// The funnel is on screen: first entry (the app sets it once `needsFunnel` holds on the 발견 tab), or reopened from 프로필
    /// `관심 분야·활동 종류`, or on the way to the 맞춤 대기열. Latched, so finishing does not close it before its last step.
    var presentingFunnel = false
    /// The answers just given are being saved and their recommendations loaded.
    private(set) var loading = false
    /// The account has 지원 자격 정보 (set by the app from the profile side); the funnel's last step says so when it does not.
    var hasEligibility = false
    /// First entry: the funnel shows until the account finished or put it off (`나중에` counts as finished; a failed save counts for this run).
    var needsFunnel: Bool { ready && preferences?.completedAt == nil }
    /// The funnel was opened on the way to the 맞춤 대기열 (its entry, or the result's `맞춤 대기열 보러 가기`): the app opens the queue once it closes.
    var queueAfterFunnel = false
    /// 사용 통계 `funnel_start` entry of the funnel being shown: `auto` (first visit to 발견), `queue_row` or `profile` (set by the app).
    @ObservationIgnored var funnelEntry = "auto"
    private var server: RecommendationServer?
    private let fetchTags: @Sendable () async throws -> [TagOption]
    init(tags: @escaping @Sendable () async throws -> [TagOption]) { fetchTags = tags }

    /// Signed out (also at launch without an account): no answers and no funnel.
    func signedOut() {
        server = nil
        preferences = nil
        ready = false
        total = 0
        presentingFunnel = false
        queueAfterFunnel = false
    }
    func signedIn(_ server: RecommendationServer) async {
        self.server = server
        ready = false
        preferences = nil
        let remote: Preferences?
        do { remote = try await server.load() } catch {
            // Unknown answers: no funnel this run (the server may have answers already).
            if self.server != nil { await refresh() }
            return
        }
        guard self.server != nil else { return }
        preferences = remote
        ready = true
        // 사용 통계 `interest_fields` names the 상위 분야, which only the tag list knows.
        if remote?.fieldTagIds.isEmpty == false { await loadTags() }
        await refresh()
    }
    /// Counts the recommendations for the answers in use (the funnel result's `n개`); a failure keeps the last count.
    func refresh() async {
        guard let server else { return }
        do {
            let loaded = try await server.recommendations()
            total = loaded?.total ?? loaded?.items.count ?? 0
        } catch {}
    }
    func loadTags() async {
        guard tags.isEmpty, let loaded = try? await fetchTags() else { return }
        tags = loaded
        publishInterests()
    }
    /// 사용 통계 user properties for the answers in use: `interest_fields` (상위 분야 English names, once the tags are known) and
    /// `interest_kinds` (활동 종류 codes in lower case); cleared without answers.
    private func publishInterests() {
        let byID = Dictionary(tags.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        var fields: [String] = []
        for tag in (preferences?.fieldTagIds ?? []).compactMap({ byID[$0] }) {
            if let name = (tag.parentID.flatMap { byID[$0] } ?? tag).nameEn, !name.isEmpty, !fields.contains(name) { fields.append(name) }
        }
        let kinds = (preferences?.categories ?? []).map { $0.lowercased() }
        if !tags.isEmpty || preferences == nil { AppAnalytics.userProperty("interest_fields", fields.isEmpty ? nil : fields.joined(separator: ",")) }
        AppAnalytics.userProperty("interest_kinds", kinds.isEmpty ? nil : kinds.joined(separator: ","))
    }
    /// The funnel ended (finished, skipped to the end, or `나중에`): recorded as finished at once so it does not show again, then saved and
    /// the recommendations for those answers load (`loading`). A failed save keeps the answers for this run only.
    func finish(_ answers: Preferences) {
        var answers = answers
        answers.completedAt = ISO8601DateFormatter().string(from: Date())
        preferences = answers
        loading = true
        Task {
            if let server, let saved = try? await server.save(answers), self.server != nil { preferences = saved }
            await refresh()
            loading = false
        }
    }
}

/// The signed-in account's 추천 calls, built by the app from `AccountClient` and `ActivityClient` with the account's token refresh.
struct RecommendationServer: Sendable {
    let load: @Sendable () async throws -> AccountClient.RecommendationPreferences?
    let save: @Sendable (AccountClient.RecommendationPreferences) async throws -> AccountClient.RecommendationPreferences
    /// Nil when the server has no recommendation API (404).
    let recommendations: @Sendable () async throws -> RecommendationPage?
}
