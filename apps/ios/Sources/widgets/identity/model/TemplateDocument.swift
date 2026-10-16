import Foundation

/// The card decoration template (contract "템플릿"): markdown with `{{profile.name}}`, `{{profile.job}}`,
/// `{{profile.introduction}}` and `{{contact:<id>}}` tokens. The app shows it filled in; the server fills it at publish.
enum TemplateDocument {
    /// A token the picker can insert: what it writes and what it shows.
    struct Token: Equatable, Identifiable {
        let id: String
        let label: String
        let value: String
        var markdown: String { "{{\(id)}}" }
    }
    // The pattern is a constant, so it always compiles; the fallback never matches.
    static let pattern = (try? NSRegularExpression(pattern: #"\{\{\s*([a-zA-Z]+)(?:[.:]([^{}\s]+))?\s*\}\}"#))
        ?? NSRegularExpression()

    /// The starter template for a new card: name, job, introduction and the phone and email tokens.
    static func starter(for profile: AccountProfile) -> String {
        var lines = ["# {{profile.name}}", "{{profile.job}}", "", "{{profile.introduction}}", ""]
        for contact in profile.contacts where contact.kind == "phone" || contact.kind == "email" {
            lines.append("- \(contact.label): {{contact:\(contact.id)}}")
        }
        return lines.joined(separator: "\n")
    }
    static func tokens(for profile: AccountProfile) -> [Token] {
        [Token(id: "profile.name", label: "이름", value: profile.name), Token(id: "profile.job", label: "직함", value: profile.job),
         Token(id: "profile.introduction", label: "소개", value: profile.introduction)]
            + profile.contacts.map { Token(id: "contact:\($0.id)", label: $0.label, value: $0.value) }
    }
    /// Contact ids the template refers to; they must be among the card's public contacts.
    static func contactIDs(in template: String) -> Set<String> {
        Set(matches(template).compactMap { $0.kind == "contact" ? $0.key.lowercased() : nil })
    }
    /// The template with tokens replaced (profile fields; only the selected contacts; everything else becomes "").
    static func fill(_ template: String, profile: AccountProfile, contactIDs: Set<String>) -> String {
        let contacts = Dictionary(uniqueKeysWithValues: profile.contacts.filter { contactIDs.contains($0.id) }.map { ($0.id.lowercased(), $0.value) })
        var result = template
        for match in matches(template).reversed() {
            let replacement: String
            switch (match.kind, match.key) {
            case ("profile", "name"): replacement = profile.name
            case ("profile", "job"): replacement = profile.job
            case ("profile", "introduction"): replacement = profile.introduction
            case ("contact", let id): replacement = contacts[id.lowercased()] ?? ""
            default: replacement = ""
            }
            result.replaceSubrange(match.range, with: replacement)
        }
        return result
    }
    struct Match { let range: Range<String.Index>; let kind: String; let key: String }
    static func matches(_ template: String) -> [Match] {
        pattern.matches(in: template, range: NSRange(template.startIndex..., in: template)).compactMap { result in
            guard let whole = Range(result.range, in: template), let kindRange = Range(result.range(at: 1), in: template) else { return nil }
            let key = Range(result.range(at: 2), in: template).map { String(template[$0]) } ?? ""
            return Match(range: whole, kind: String(template[kindRange]), key: key)
        }
    }
}
