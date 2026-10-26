import Foundation

struct ContactModel: Identifiable, Equatable {
    let id: String
    let kind: String
    var displayLabel: String
    var value: String
    enum Action: Equatable { case open(URL), copy(String) }
    /// 바로가기 (contract "연락처 아이콘·바로가기" 2026-10-26): phone `tel:`, email `mailto:`, a GitHub·Instagram·Behance username as its profile
    /// page (the system opens the app when installed). A KakaoTalk ID has no public link, so it is copied; an open-chat URL saved before
    /// 2026-10-26 still opens. Nil when the value fits none of these.
    var action: Action? {
        let text = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        switch kind {
        case "phone": return URL(string: "tel:" + text.filter { $0.isNumber || $0 == "+" }).map(Action.open)
        case "email": return URL(string: "mailto:" + text).map(Action.open)
        case "kakao": return Self.web(text).map(Action.open) ?? .copy(Self.username(kind: kind, text) ?? text)
        default:
            guard let host = Self.usernameRules[kind]?.host, let id = Self.username(kind: kind, text) else { return Self.web(text).map(Action.open) }
            return URL(string: "https://" + (kind == "behance" ? "www." : "") + host + "/" + id).map(Action.open)
        }
    }
    /// The value as rows show it: the username for username kinds (also for a profile URL saved before 2026-10-26), otherwise as saved.
    var displayValue: String { Self.username(kind: kind, value) ?? value }
    /// An http(s) URL, also typed without the scheme (`open.kakao.com/o/…`).
    private static func web(_ text: String) -> URL? {
        let candidate = text.contains("://") ? text : text.contains("/") && text.split(separator: "/")[0].contains(".") ? "https://" + text : nil
        guard let candidate, let url = URL(string: candidate), ["http", "https"].contains(url.scheme?.lowercased()), url.host?.isEmpty == false else { return nil }
        return url
    }
    /// 연락처 사용자명 입력 (contract "연락처 사용자명 입력" 2026-10-26): these kinds take a username only — 영문·숫자·`.`·`_`·`-` up to `max`;
    /// the app builds the link from `host`. KakaoTalk has no link. Same table as `PUT /v1/profile`.
    static let usernameRules: [String: (host: String?, max: Int)] = [
        "github": ("github.com", 39), "instagram": ("instagram.com", 30), "behance": ("behance.net", 64), "kakao": (nil, 20),
    ]
    /// The fixed text shown before a username field (`github.com/`); nil for other kinds and KakaoTalk.
    static func usernamePrefix(kind: String) -> String? { usernameRules[kind]?.host.map { $0 + "/" } }
    /// The username in `text` — a pasted profile URL (`https://github.com/name`) gives its first path part, `@` and spaces are dropped —
    /// or nil when what is left is empty, too long or has other characters.
    static func username(kind: String, _ text: String) -> String? {
        guard let rule = usernameRules[kind] else { return nil }
        let id = profilePath(text, host: rule.host).filter { !$0.isWhitespace && $0 != "@" }
        return !id.isEmpty && id.count <= rule.max && id.allSatisfy(isUsernameCharacter) ? id : nil
    }
    /// What typing or pasting leaves in a username field: the allowed characters of the username, up to the length. A URL in a KakaoTalk
    /// field is not an ID and leaves the field empty. Other kinds are left as typed.
    static func sanitizedUsername(kind: String, _ text: String) -> String {
        guard let rule = usernameRules[kind] else { return text }
        if rule.host == nil, text.contains("/") { return "" }
        return String(profilePath(text, host: rule.host).filter(isUsernameCharacter).prefix(rule.max))
    }
    private static func isUsernameCharacter(_ c: Character) -> Bool { c.isASCII && (c.isLetter || c.isNumber || "._-".contains(c)) }
    /// `[https://][www.|m.]host/name[/…]` → `name`; anything else as it is.
    private static func profilePath(_ text: String, host: String?) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let host, let range = trimmed.range(of: "^(?i)(https?://)?((www|m)\\.)?" + NSRegularExpression.escapedPattern(for: host) + "/", options: .regularExpression)
        else { return trimmed }
        return String(trimmed[range.upperBound...].prefix { !"/?#".contains($0) })
    }
    /// Row-leading glyph (contract "연락처 아이콘·바로가기" 2026-10-26): GitHub·Instagram·Behance·KakaoTalk are the brand marks as template
    /// images (원본 `shared/assets/prototype/contact-icons`, Simple Icons) and the rest SF Symbols; every place draws them in one color.
    static func glyph(kind: String) -> DearbyGlyph {
        switch kind {
        case "github": .asset("ContactGitHub")
        case "instagram": .asset("ContactInstagram")
        case "behance": .asset("ContactBehance")
        case "kakao": .asset("ContactKakaoTalk")
        case "phone": .system("phone")
        case "email": .system("envelope")
        default: .system("link")
        }
    }
    /// The kind's name (contract vocabulary phone/email/kakao/instagram/github/behance), shown when `displayLabel` is empty.
    var kindName: String {
        switch kind {
        case "phone": "전화"
        case "email": "이메일"
        case "kakao": "카카오톡"
        case "instagram": "Instagram"
        case "github": "GitHub"
        case "behance": "Behance"
        default: "링크"
        }
    }
    /// Row label: the owner's `label`, or the kind name when none was given.
    var rowLabel: String { displayLabel.isEmpty ? kindName : displayLabel }
}
/// A history as shown on cards and profiles: `title` is 조직 → 프로그램 → 활동, `verified` hides the 미확인 badge.
struct HistoryModel: Identifiable, Equatable {
    let id: String
    var title: String
    var role: String
    var startDate: String
    var endDate: String?
    var verified = false
    var description = ""
}
struct CardModel: Identifiable, Equatable {
    let id: String
    var name: String
    var profileName: String
    var job: String
    /// 소개 링크 copied at publish (contract "소개 링크·소개 글 폐지" 2026-10-26), shown beside the name.
    var introLink: String?
    var description: String
    var contacts: [ContactModel]
    var histories: [HistoryModel]
    /// Organization of the most recent approved history, shown under the name (contract "명함 표현").
    var organization: String?
    /// The photo copied at publish (`PublishedCard.photoUrl`); nil shows the initial.
    var photoUrl: String? = nil
    /// The 소개 링크 when it is an https address (the server only stores those).
    var introURL: URL? {
        guard let url = introLink.flatMap(URL.init(string:)), url.scheme?.lowercased() == "https", url.host?.isEmpty == false else { return nil }
        return url
    }
    /// `job` split on ` · ` for the header chips.
    var jobs: [String] { job.components(separatedBy: " · ").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty } }
}
extension CardModel {
    /// Sample values for a loading card (`dearbySkeleton`, contract "내용 로딩은 스켈레톤" 2026-10-22); never shown unredacted.
    static let placeholder = CardModel(
        id: "", name: "내 명함", profileName: "이름", job: "직무 · 직무", introLink: nil,
        description: "", contacts: [ContactModel(id: "p", kind: "email", displayLabel: "", value: "name@example.com")],
        histories: [HistoryModel(id: "h", title: "조직 · 프로그램 · 활동", role: "역할", startDate: "2026.01", endDate: "진행 중")],
        organization: "조직 이름")
}
