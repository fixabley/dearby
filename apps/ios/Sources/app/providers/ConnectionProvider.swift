import Foundation
import OSLog

private let log = Logger(subsystem: "com.dearby.app", category: "connection")

/// Owns the build-injected API origin: the account client and activity loads use it.
struct ConnectionProvider: Sendable {
    let origins: AppOrigins
    /// Info.plist `DearbyAppleSignInEnabled`: false until the app ID has Sign in with Apple and the
    /// entitlements carry `com.apple.developer.applesignin` (see README "애플 로그인").
    let appleSignInEnabled: Bool
    /// Info.plist `GIDClientID` (build setting `GOOGLE_IOS_CLIENT_ID`): the Google button needs an iOS client ID.
    let googleSignInEnabled: Bool
    init(info: [String: Any] = Bundle.main.infoDictionary ?? [:]) {
        var info = info
        #if DEBUG
        let debug = true
        // Debug builds may point at another API through the launch environment; Release has no override.
        if let override = ProcessInfo.processInfo.environment["DEARBY_API_ORIGIN"] { info["DearbyAPIOrigin"] = override }
        if let override = ProcessInfo.processInfo.environment["DEARBY_WEB_ORIGIN"] { info["DearbyWebOrigin"] = override }
        #else
        let debug = false
        #endif
        guard let origins = AppOrigins.load(info, debug: debug) else {
            preconditionFailure("DearbyAPIOrigin is missing or invalid.")
        }
        self.origins = origins
        appleSignInEnabled = info["DearbyAppleSignInEnabled"] as? Bool ?? false
        googleSignInEnabled = GoogleNativeSignIn.isConfigured(info)
        #if DEBUG
        // A Debug build without DEARBY_API_ORIGIN talks to a local API; say so once.
        if origins.api.host == "localhost" || origins.api.host == "127.0.0.1" {
            log.warning("Debug build uses the local API \(origins.api.absoluteString, privacy: .public); set DEARBY_API_ORIGIN to use another API.")
        }
        #endif
    }
    @MainActor func account() -> AccountViewModel {
        var vault = TokenVault()
        #if DEBUG
        // A fresh Keychain item per launch environment keeps one run's sign-in from leaking into another.
        if let service = ProcessInfo.processInfo.environment["DEARBY_TOKEN_SERVICE"] { vault.service = service }
        #endif
        return AccountViewModel(client: .live(api: origins.api), vault: vault, appleSignInEnabled: appleSignInEnabled, googleSignInEnabled: googleSignInEnabled)
    }
    /// Programs for the discovery accordion (contract "발견 프로그램 묶음"); `openOnly` false also lists recently active ones.
    func programs(query: String, cursor: Int64?, openOnly: Bool) async throws -> ProgramPage {
        do { return try await ActivityClient.fetchPrograms(api: origins.api, query: query, cursor: cursor, openOnly: openOnly) } catch {
            // Only the origin and the failure kind; the search text is not logged.
            log.warning("Program load from \(origins.api.absoluteString, privacy: .public) failed: \(String(describing: error), privacy: .public)")
            throw error
        }
    }
    /// One activity for a 내 활동 record; nil when it is gone (404).
    func activity(id: String) async throws -> ActivityModel? {
        do { return try await ActivityClient.fetch(api: origins.api, id: id) } catch {
            log.warning("Activity load from \(origins.api.absoluteString, privacy: .public) failed: \(String(describing: error), privacy: .public)")
            throw error
        }
    }
    /// 내 활동 on the server for the signed-in `account` (contract "내 활동 서버 저장"); each call refreshes the tokens once on a 401.
    @MainActor func mineServer(_ account: AccountViewModel) -> MineServer {
        let client = account.client
        return MineServer(
            list: { try await account.authorized { try await client.myActivities($0) } },
            put: { id in try await account.authorized { try await client.putMyActivity(id, $0) } },
            delete: { id in try await account.authorized { try await client.deleteMyActivity(id, $0) } },
            putStatus: { id, schedule, status, kind in
                try await account.authorized { try await client.putMyActivityStatus(id, scheduleID: schedule, statusID: status, kind: kind, $0) }
            },
            importItems: { items in try await account.authorized { try await client.importMyActivities(items, $0) } })
    }
    /// 추천 while signed out: the device answers go in the query. Nil when the server has no recommendation API (404).
    func recommendations(_ preferences: AccountClient.RecommendationPreferences) async throws -> RecommendationPage? {
        try await ActivityClient.fetchRecommendations(api: origins.api, size: 10, fieldTagIDs: preferences.fieldTagIds,
                                                      categories: preferences.categories, traitTagIDs: preferences.traitTagIds, bearer: nil)
    }
    /// 맞춤 대기열 while signed out (contract "맞춤 대기열" 2026-10-24): 40 추천 results, so `더 받기` has others to show.
    func queueCandidates(_ preferences: AccountClient.RecommendationPreferences) async throws -> RecommendationPage? {
        try await ActivityClient.fetchRecommendations(api: origins.api, size: 40, fieldTagIDs: preferences.fieldTagIds,
                                                      categories: preferences.categories, traitTagIDs: preferences.traitTagIds, bearer: nil)
    }
    /// 맞춤 대기열 for the signed-in `account`; each call refreshes the tokens once on a 401.
    @MainActor func discoveryQueueServer(_ account: AccountViewModel) -> DiscoveryQueueServer {
        let api = origins.api
        @Sendable func authorized<Value: Sendable>(_ call: @escaping @Sendable (String) async throws -> Value) async throws -> Value {
            try await account.authorized { session in
                do { return try await call(session.accessToken) } catch ActivityClient.RecommendationFailure.unauthorized { throw AccountError.unauthorized }
            }
        }
        return DiscoveryQueueServer(
            load: { try await authorized { try await ActivityClient.fetchDiscoveryQueue(api: api, bearer: $0) } },
            refill: { try await authorized { try await ActivityClient.refillDiscoveryQueue(api: api, bearer: $0) } },
            signal: { id, kind, tag in
                try await authorized { try await ActivityClient.sendRecommendationSignal(api: api, activityID: id, kind: kind, tagID: tag, bearer: $0) }
            })
    }
    func tagOptions() async throws -> [TagOption] { try await ActivityClient.fetchTagOptions(api: origins.api) }
    /// 추천 for the signed-in `account` (saved answers and 지원 자격 정보 on the server); each call refreshes the tokens once on a 401.
    @MainActor func recommendationServer(_ account: AccountViewModel) -> RecommendationServer {
        let client = account.client, api = origins.api
        return RecommendationServer(
            load: { try await account.authorized { try await client.recommendationPreferences($0) } },
            save: { preferences in try await account.authorized { try await client.saveRecommendationPreferences(preferences, $0) } },
            recommendations: {
                try await account.authorized { session in
                    do {
                        return try await ActivityClient.fetchRecommendations(api: api, size: 10, fieldTagIDs: [], categories: [], traitTagIDs: [],
                                                                             bearer: session.accessToken)
                    } catch ActivityClient.RecommendationFailure.unauthorized { throw AccountError.unauthorized }
                }
            })
    }
    /// One program's activities: the open ones (server-judged, up to 100) or, for the past list, the first 20 of every activity.
    func programActivities(programID: Int64, open: Bool) async throws -> ActivityPage {
        do { return try await ActivityClient.fetch(api: origins.api, query: "", cursor: nil, programID: programID, size: open ? 100 : 20, open: open) } catch {
            log.warning("Activity load from \(origins.api.absoluteString, privacy: .public) failed: \(String(describing: error), privacy: .public)")
            throw error
        }
    }
}
