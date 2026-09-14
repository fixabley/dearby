import Foundation

/// Only explicitly delimited room/floor suffixes move to a second line; ambiguous names stay whole.
struct NoticePlaceState {
    let name: String
    let detail: String?

    init(name: String, address: String?) {
        var main = name
        var parts: [String] = []
        if let open = name.lastIndex(of: "("), name.hasSuffix(")") {
            let inner = String(name[name.index(after: open)..<name.index(before: name.endIndex)])
            if inner.hasSuffix("층") || inner.hasSuffix("호") || inner.hasSuffix("호실") {
                main = String(name[..<open]).trimmingCharacters(in: .whitespaces)
                parts.append(inner)
            }
        } else if let space = name.lastIndex(of: " ") {
            let suffix = String(name[name.index(after: space)...])
            if suffix.first?.isNumber == true && (suffix.hasSuffix("호") || suffix.hasSuffix("호실")) {
                main = String(name[..<space]); parts.append(suffix)
            }
        }
        if let address, !address.isEmpty, address != name, address != main, !parts.contains(address) { parts.append(address) }
        self.name = main
        detail = parts.isEmpty ? nil : parts.joined(separator: "\n")
    }

    static func safeOnlineURL(_ raw: String?) -> URL? {
        guard let raw, let url = URL(string: raw), ["https", "http"].contains(url.scheme?.lowercased() ?? ""),
              let host = url.host, !host.isEmpty, url.user == nil, url.password == nil,
              !raw.contains(where: \.isWhitespace) else { return nil }
        return url
    }
}
