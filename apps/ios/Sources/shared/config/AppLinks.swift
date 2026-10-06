import Foundation

/// API and web origins injected at build time (Info.plist `DearbyAPIOrigin`, `DearbyWebOrigin`).
struct AppOrigins: Equatable {
    let api: URL
    let web: URL

    /// Release: `https://<domain>` only. Debug also allows local http servers and falls back to them when unset.
    static func load(_ info: [String: Any], debug: Bool) -> AppOrigins? {
        func origin(_ key: String, fallback: String) -> URL? {
            let raw = info[key] as? String ?? ""
            return parse(raw.isEmpty && debug ? fallback : raw, debug: debug)
        }
        guard let api = origin("DearbyAPIOrigin", fallback: "http://localhost:3000"),
              let web = origin("DearbyWebOrigin", fallback: "http://localhost:3210") else { return nil }
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

/// Shared-card links: exactly `<web origin>/s/<UUID>` → lower-case share ID; query, fragment or anything else is ignored.
enum SharedCardLink {
    /// The link a share's QR carries: `<web origin>/s/<share ID>`.
    static func url(shareID: String, web: URL) -> URL { web.appending(path: "s/\(shareID)") }
    static func shareID(from url: URL, web: URL) -> String? {
        guard let parts = URLComponents(url: url, resolvingAgainstBaseURL: false),
              parts.scheme == web.scheme, parts.host?.lowercased() == web.host, parts.port == web.port,
              parts.query == nil, parts.fragment == nil else { return nil }
        let path = parts.path.split(separator: "/", omittingEmptySubsequences: true)
        guard path.count == 2, path[0] == "s", let id = UUID(uuidString: String(path[1])) else { return nil }
        return id.uuidString.lowercased()
    }
}
