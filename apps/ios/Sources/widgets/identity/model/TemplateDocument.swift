import Foundation

/// The built-in card template (contract "템플릿"): markdown with `{{profile.name}}`, `{{profile.job}}`,
/// `{{profile.introduction}}` and `{{contact:<id>}}` tokens. The app previews it filled in; the server fills it at publish.
/// Users cannot edit it in the app (2026-10-18).
enum TemplateDocument {
    // The pattern is a constant, so it always compiles; the fallback never matches.
    static let pattern = (try? NSRegularExpression(pattern: #"\{\{\s*([a-zA-Z]+)(?:[.:]([^{}\s]+))?\s*\}\}"#))
        ?? NSRegularExpression()

    /// The starter template for a new card: the self-introduction only. Name, job and contacts are drawn by the card face
    /// itself (contract "명함 표현"), so repeating them here showed the same text twice (2026-10-18).
    static func starter(for profile: AccountProfile) -> String { "{{profile.introduction}}" }
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
    private struct Match { let range: Range<String.Index>; let kind: String; let key: String }
    private static func matches(_ template: String) -> [Match] {
        pattern.matches(in: template, range: NSRange(template.startIndex..., in: template)).compactMap { result in
            guard let whole = Range(result.range, in: template), let kindRange = Range(result.range(at: 1), in: template) else { return nil }
            let key = Range(result.range(at: 2), in: template).map { String(template[$0]) } ?? ""
            return Match(range: whole, kind: String(template[kindRange]), key: key)
        }
    }
}
