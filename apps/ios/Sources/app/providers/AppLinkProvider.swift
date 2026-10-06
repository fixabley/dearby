import Foundation
import OSLog

private let log = Logger(subsystem: "com.dearby.app", category: "connection")

/// Owns the build-injected origins: universal links become share IDs and API calls use the API origin.
struct AppLinkProvider: Sendable {
    let origins: AppOrigins
    init(info: [String: Any] = Bundle.main.infoDictionary ?? [:]) {
        var info = info
        #if DEBUG
        let debug = true
        // UI tests point Debug builds at their own fixture server; Release has no override.
        if let override = ProcessInfo.processInfo.environment["DEARBY_API_ORIGIN"] { info["DearbyAPIOrigin"] = override }
        #else
        let debug = false
        #endif
        guard let origins = AppOrigins.load(info, debug: debug) else {
            preconditionFailure("DearbyAPIOrigin/DearbyWebOrigin are missing or invalid.")
        }
        self.origins = origins
        #if DEBUG
        // A Debug build without DEARBY_API_ORIGIN talks to a local API; say so once.
        if origins.api.host == "localhost" || origins.api.host == "127.0.0.1" {
            log.warning("Debug build uses the local API \(origins.api.absoluteString, privacy: .public); set DEARBY_API_ORIGIN to use another API.")
        }
        #endif
    }
    func shareURL(_ shareID: String) -> URL { SharedCardLink.url(shareID: shareID, web: origins.web) }
    func scanned(_ text: String) -> ScannedLink? { ScannedLink.parse(text, web: origins.web) }
    func shareID(_ url: URL) -> String? { SharedCardLink.shareID(from: url, web: origins.web) }
    @MainActor func account() -> AccountViewModel {
        var vault = SessionVault()
        #if DEBUG
        // UI tests use a fresh Keychain item per run so one test's sign-in never leaks into another.
        if let service = ProcessInfo.processInfo.environment["DEARBY_SESSION_SERVICE"] { vault.service = service }
        #endif
        return AccountViewModel(client: .live(api: origins.api), vault: vault)
    }
    func catalog() async throws -> [ActivityModel] {
        do { return try await CatalogClient.fetch(api: origins.api) } catch {
            // Only the origin and the failure kind: nothing personal is in a catalog request.
            log.warning("Catalog load from \(origins.api.absoluteString, privacy: .public) failed: \(String(describing: error), privacy: .public)")
            throw error
        }
    }
}
