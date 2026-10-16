import Foundation

/// The markdown subset cards use (contract "템플릿"): headings `#`–`###`, bold, italic, bullet and numbered lists,
/// links (https, mailto, tel) and line breaks. Raw HTML is left as text. Parsing is line based and never fails.
enum MarkdownDocument {
    enum Block: Equatable {
        case heading(level: Int, text: String)
        case list(ordered: Bool, items: [String])
        case paragraph(lines: [String])
    }

    static func blocks(_ source: String) -> [Block] {
        var result: [Block] = []
        for line in source.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n").split(separator: "\n", omittingEmptySubsequences: false).map(String.init) {
            if let heading = heading(line) {
                result.append(.heading(level: heading.level, text: heading.text))
            } else if let item = listItem(line) {
                if case .list(let ordered, var items) = result.last, ordered == item.ordered {
                    items.append(item.text)
                    result[result.count - 1] = .list(ordered: ordered, items: items)
                } else {
                    result.append(.list(ordered: item.ordered, items: [item.text]))
                }
            } else if line.trimmingCharacters(in: .whitespaces).isEmpty {
                result.append(.paragraph(lines: []))
            } else if case .paragraph(var lines) = result.last, !lines.isEmpty {
                lines.append(line)
                result[result.count - 1] = .paragraph(lines: lines)
            } else {
                result.append(.paragraph(lines: [line]))
            }
        }
        return result.filter { if case .paragraph(let lines) = $0 { !lines.isEmpty } else { true } }
    }

    /// Inline bold/italic/links as an AttributedString (Foundation's markdown parser, inline only, HTML kept as text).
    static func inline(_ text: String) -> AttributedString {
        let options = AttributedString.MarkdownParsingOptions(allowsExtendedAttributes: false, interpretedSyntax: .inlineOnly, failurePolicy: .returnPartiallyParsedIfPossible)
        guard var parsed = try? AttributedString(markdown: text, options: options) else { return AttributedString(text) }
        // Links outside https/mailto/tel are shown as plain text.
        for run in parsed.runs where run.link != nil {
            let scheme = run.link?.scheme?.lowercased() ?? ""
            if !["https", "mailto", "tel"].contains(scheme) { parsed[run.range].link = nil }
        }
        return parsed
    }

    private static func heading(_ line: String) -> (level: Int, text: String)? {
        let hashes = line.prefix { $0 == "#" }
        guard (1...3).contains(hashes.count), line.dropFirst(hashes.count).first == " " else { return nil }
        return (hashes.count, String(line.dropFirst(hashes.count + 1)))
    }
    private static func listItem(_ line: String) -> (ordered: Bool, text: String)? {
        let trimmed = line.drop { $0 == " " || $0 == "\t" }
        if let first = trimmed.first, "-*+".contains(first), trimmed.dropFirst().first == " " { return (false, String(trimmed.dropFirst(2))) }
        let digits = trimmed.prefix { $0.isNumber }
        guard !digits.isEmpty, let mark = trimmed.dropFirst(digits.count).first, mark == "." || mark == ")",
              trimmed.dropFirst(digits.count + 1).first == " " else { return nil }
        return (true, String(trimmed.dropFirst(digits.count + 2)))
    }
}
