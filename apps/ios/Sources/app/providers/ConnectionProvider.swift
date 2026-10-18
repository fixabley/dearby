import Foundation
import OSLog

private let log = Logger(subsystem: "com.dearby.app", category: "connection")

/// Owns the build-injected API origin: the account client and activity loads use it.
struct ConnectionProvider: Sendable {
    let origins: AppOrigins
    /// Info.plist `DearbyAppleSignInEnabled`: false until the app ID has Sign in with Apple and the
    /// entitlements carry `com.apple.developer.applesignin` (see README "애플 로그인").
    let appleSignInEnabled: Bool
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
        return AccountViewModel(client: .live(api: origins.api), vault: vault, appleSignInEnabled: appleSignInEnabled)
    }
    /// Programs with open activities for the discovery accordion (contract "발견 프로그램 묶음").
    func programs(query: String, cursor: Int64?) async throws -> ProgramPage {
        do { return try await ActivityClient.fetchPrograms(api: origins.api, query: query, cursor: cursor) } catch {
            // Only the origin and the failure kind; the search text is not logged.
            log.warning("Program load from \(origins.api.absoluteString, privacy: .public) failed: \(String(describing: error), privacy: .public)")
            throw error
        }
    }
    /// One program's activities (first page of up to 100), filtered to the open ones by the caller.
    func programActivities(programID: Int64) async throws -> ActivityPage {
        do { return try await ActivityClient.fetch(api: origins.api, query: "", cursor: nil, programID: programID, size: 100) } catch {
            log.warning("Activity load from \(origins.api.absoluteString, privacy: .public) failed: \(String(describing: error), privacy: .public)")
            throw error
        }
    }
}
