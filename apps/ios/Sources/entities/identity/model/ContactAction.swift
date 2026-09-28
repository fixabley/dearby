import Foundation

enum ContactAction {
    case open(URL), copy(String), unavailable
}
extension ContactModel {
    var symbol: String {
        switch kind {
        case "phone": "phone"
        case "email": "envelope"
        case "kakao": "bubble.left"
        case "instagram": "camera"
        case "github": "chevron.left.forwardslash.chevron.right"
        default: "paintbrush"
        }
    }
    var displayLabel: String { label.isEmpty ? kind : label }
    var action: ContactAction {
        let raw = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if kind == "kakao", !raw.contains(":"), !raw.isEmpty { return .copy(raw) }
        var components = URLComponents()
        if kind == "phone" {
            let phone = raw.filter { !$0.isWhitespace && $0 != "-" }
            guard !phone.isEmpty, phone.allSatisfy({ "+0123456789".contains($0) }) else { return .unavailable }
            components.scheme = "tel"; components.path = phone
        } else if kind == "email" {
            guard raw.contains("@"), !raw.contains(where: { $0.isWhitespace }), !raw.contains("?"), !raw.contains("#") else {
                return .unavailable
            }
            components.scheme = "mailto"; components.path = raw
        } else {
            guard let url = URL(string: raw), url.scheme == "https", url.host != nil,
                  url.user == nil, url.password == nil else { return .unavailable }
            return .open(url)
        }
        return components.url.map(ContactAction.open) ?? .unavailable
    }
}
