import Foundation

/// What a scanned QR or opened link points at: a share (`<web>/s/<UUID>`) or, from older codes, a card (`dearby://card/<UUID>`).
enum ScannedLink: Equatable, Identifiable {
    case share(String), card(String)
    var id: String { switch self { case .share(let id): "share-\(id)" case .card(let id): "card-\(id)" } }

    static func parse(_ text: String, web: URL) -> ScannedLink? {
        guard let url = URL(string: text.trimmingCharacters(in: .whitespacesAndNewlines)) else { return nil }
        if let id = SharedCardLink.shareID(from: url, web: web) { return .share(id) }
        guard let parts = URLComponents(url: url, resolvingAgainstBaseURL: false), parts.scheme == "dearby", parts.host == "card",
              parts.query == nil, parts.fragment == nil else { return nil }
        let path = parts.path.split(separator: "/")
        guard path.count == 1, let id = UUID(uuidString: String(path[0])) else { return nil }
        return .card(id.uuidString.lowercased())
    }
}
