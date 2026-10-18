import Foundation

/// The API origin injected at build time (Info.plist `DearbyAPIOrigin`) and the web origin the card QR points at
/// (`DearbyWebOrigin`, contract "명함 교환"; the production site when the build setting is empty).
struct AppOrigins: Equatable {
    let api: URL
    let web: URL
    static let defaultWeb = "https://dearby.wid.io.kr"

    /// Release: `https://<domain>` only. Debug also allows local http servers and falls back to one when unset.
    static func load(_ info: [String: Any], debug: Bool) -> AppOrigins? {
        let raw = info["DearbyAPIOrigin"] as? String ?? ""
        guard let api = parse(raw.isEmpty && debug ? "http://localhost:3000" : raw, debug: debug) else { return nil }
        let rawWeb = info["DearbyWebOrigin"] as? String ?? ""
        guard let web = parse(rawWeb.isEmpty ? defaultWeb : rawWeb, debug: debug) else { return nil }
        return AppOrigins(api: api, web: web)
    }

    static func parse(_ raw: String, debug: Bool) -> URL? {
        guard let parts = URLComponents(string: raw), let host = parts.host, host == host.lowercased(),
              parts.path.isEmpty, parts.query == nil, parts.fragment == nil, parts.user == nil else { return nil }
        if parts.scheme == "https", parts.port == nil,
           host.contains("."), host.split(separator: ".").last?.first?.isLetter == true {
            return parts.url
        }
        return debug && parts.scheme == "http" && ["localhost", "127.0.0.1"].contains(host) ? parts.url : nil
    }
}
