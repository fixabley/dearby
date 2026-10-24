import Foundation
import Observation

/// 추천 (contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24): the funnel's answers and the 발견 tab's 추천 section.
/// Signed out, the answers live on the device (`preferencesKey`, a new key; no earlier app data is read) and go to the server as a query.
/// Signed in, `signedIn(_:)` reads the account's answers; when it has none, the device answers are uploaded once.
/// A server without the recommendation API (404) hides the funnel and the section; nothing else changes.
@MainActor @Observable final class RecommendationModel {
    typealias Preferences = AccountClient.RecommendationPreferences
    /// The answers in use; nil until they are read, or when there are none yet.
    private(set) var preferences: Preferences?
    /// The answers have been read (device or server), so the funnel may decide whether to show.
    private(set) var ready = false
    /// The server answered the recommendation API; false hides everything here.
    private(set) var available = false
    private(set) var items: [RecommendedActivity] = []
    private(set) var tags: [TagOption] = []
    /// The funnel is on screen: first entry (the app sets it once `needsFunnel` holds on the 발견 tab), or reopened from 프로필
    /// `관심 분야·활동 종류`, the section's `설정` or `나에게 맞는 활동 찾기`. Latched, so finishing does not close it before its last step.
    var presentingFunnel = false
    /// The answers just given are being saved and their recommendations loaded.
    private(set) var loading = false
    /// The account has 지원 자격 정보 (set by the app from the profile side); the funnel's last step says so when it does not.
    var hasEligibility = false
    /// First entry: the funnel shows until it is finished or put off (`나중에` counts as finished).
    var needsFunnel: Bool { ready && available && preferences?.completedAt == nil }
    /// The 추천 section: finished with something picked. Finished with nothing picked shows `나에게 맞는 활동 찾기` instead.
    var showsSection: Bool { available && preferences.map { $0.completedAt != nil && !$0.isEmpty } == true }
    var showsFinder: Bool { available && preferences.map { $0.completedAt != nil && $0.isEmpty } == true }
    static let preferencesKey = "dearby.recommendation.preferences"
    private var server: RecommendationServer?
    private let fetchAnonymous: @Sendable (Preferences) async throws -> [RecommendedActivity]?
    private let fetchTags: @Sendable () async throws -> [TagOption]
    private let defaults: UserDefaults
    init(recommendations: @escaping @Sendable (Preferences) async throws -> [RecommendedActivity]?,
         tags: @escaping @Sendable () async throws -> [TagOption], defaults: UserDefaults = .standard) {
        fetchAnonymous = recommendations
        fetchTags = tags
        self.defaults = defaults
    }

    /// Signed out (also at launch without an account): the device answers.
    func signedOut() async {
        server = nil
        preferences = deviceValue
        ready = true
        await refresh()
    }
    func signedIn(_ server: RecommendationServer) async {
        self.server = server
        ready = false
        preferences = nil
        var remote: Preferences?
        do { remote = try await server.load() } catch {
            // Unknown answers: no funnel and no upload this run (the server may have answers already).
            if self.server != nil { await refresh() }
            return
        }
        if remote?.completedAt == nil, remote?.isEmpty != false, let local = deviceValue, local.completedAt != nil {
            remote = (try? await server.save(local)) ?? local
        }
        guard self.server != nil else { return }
        preferences = remote
        ready = true
        await refresh()
    }
    /// Loads the recommendations for the answers in use. A failure other than 404 keeps what is shown.
    func refresh() async {
        let preferences = preferences ?? Preferences()
        do {
            let loaded = if let server { try await server.recommendations() } else { try await fetchAnonymous(preferences) }
            available = loaded != nil
            items = loaded ?? []
        } catch {}
    }
    func loadTags() async {
        guard tags.isEmpty, let loaded = try? await fetchTags() else { return }
        tags = loaded
    }
    /// The funnel ended (finished, skipped to the end, or `나중에`): recorded as finished at once so it does not show again, then saved and
    /// the recommendations for those answers load (`loading`). Signed in, a failed save keeps the answers for this run only.
    func finish(_ answers: Preferences) {
        var answers = answers
        answers.completedAt = ISO8601DateFormatter().string(from: Date())
        preferences = answers
        if server == nil, let data = try? JSONEncoder().encode(answers) { defaults.set(data, forKey: Self.preferencesKey) }
        loading = true
        Task {
            if let server, let saved = try? await server.save(answers), self.server != nil { preferences = saved }
            await refresh()
            loading = false
        }
    }
    private var deviceValue: Preferences? {
        defaults.data(forKey: Self.preferencesKey).flatMap { try? JSONDecoder().decode(Preferences.self, from: $0) }
    }
}

/// The signed-in account's 추천 calls, built by the app from `AccountClient` and `ActivityClient` with the account's token refresh.
struct RecommendationServer: Sendable {
    let load: @Sendable () async throws -> AccountClient.RecommendationPreferences?
    let save: @Sendable (AccountClient.RecommendationPreferences) async throws -> AccountClient.RecommendationPreferences
    /// Nil when the server has no recommendation API (404).
    let recommendations: @Sendable () async throws -> [RecommendedActivity]?
}
