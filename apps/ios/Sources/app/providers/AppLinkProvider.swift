import Foundation

/// Owns the build-injected origins and turns incoming universal links into share IDs.
struct AppLinkProvider {
    let origins: AppOrigins
    init(info: [String: Any] = Bundle.main.infoDictionary ?? [:]) {
        #if DEBUG
        let debug = true
        #else
        let debug = false
        #endif
        guard let origins = AppOrigins.load(info, debug: debug) else {
            preconditionFailure("DearbyAPIOrigin/DearbyWebOrigin are missing or invalid.")
        }
        self.origins = origins
    }
    func shareID(_ url: URL) -> String? { SharedCardLink.shareID(from: url, web: origins.web) }
}
