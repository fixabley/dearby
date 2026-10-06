import Foundation

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
    }
    func shareID(_ url: URL) -> String? { SharedCardLink.shareID(from: url, web: origins.web) }
    func catalog() async throws -> [ActivityModel] { try await CatalogClient.fetch(api: origins.api) }
}
