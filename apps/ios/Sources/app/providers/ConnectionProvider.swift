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
    func programs(query: String, cursor: Int64?, openOnly: Bool, tagIDs: [Int64]) async throws -> ProgramPage {
        do { return try await ActivityClient.fetchPrograms(api: origins.api, query: query, cursor: cursor, openOnly: openOnly, tagIDs: tagIDs) } catch {
            // Only the origin and the failure kind; the search text is not logged.
            log.warning("Program load from \(origins.api.absoluteString, privacy: .public) failed: \(String(describing: error), privacy: .public)")
            throw error
        }
    }
    /// The tag vocabulary for the discovery filter chips (contract "태그").
    func tags() async throws -> [Tag] {
        do { return try await ActivityClient.fetchTags(api: origins.api) } catch {
            log.warning("Tag load from \(origins.api.absoluteString, privacy: .public) failed: \(String(describing: error), privacy: .public)")
            throw error
        }
    }
    /// One program's activities: the open ones (server-judged, up to 100) or, for the past list, the first 20 of every activity.
    func programActivities(programID: Int64, open: Bool, tagIDs: [Int64]) async throws -> ActivityPage {
        do { return try await ActivityClient.fetch(api: origins.api, query: "", cursor: nil, programID: programID, size: open ? 100 : 20, open: open, tagIDs: tagIDs) } catch {
            log.warning("Activity load from \(origins.api.absoluteString, privacy: .public) failed: \(String(describing: error), privacy: .public)")
            throw error
        }
    }
}
