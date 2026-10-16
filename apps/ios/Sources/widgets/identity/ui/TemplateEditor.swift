import SwiftUI

/// Edits the card body right on the rendered view. On iOS 26 the rich `TextEditor` shows the markdown as formatting
/// and data tokens as filled-in, locked runs; earlier systems edit the markdown source with a live preview below.
struct TemplateEditor: View {
    @Binding var template: String
    let tokens: [TemplateDocument.Token]
    let preview: String
    var body: some View {
        if #available(iOS 26.0, *) {
            RichTemplateEditor(template: $template, tokens: tokens)
        } else {
            VStack(alignment: .leading, spacing: 10) {
                TextEditor(text: $template).font(.dearby(.body)).frame(minHeight: 160)
                    .padding(8).background(DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 10)).accessibilityLabel("명함 본문 마크다운")
                Text("미리보기").font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                DearbyMarkdownText(source: preview).padding(12).overlay(RoundedRectangle(cornerRadius: 10).stroke(DearbyStyle.line))
            }
        }
    }
}

/// Custom attributes carried by editor runs: which block a paragraph is, and which data token a run stands for.
enum TemplateAttributes {
    struct BlockKey: CodableAttributedStringKey { typealias Value = String; static let name = "dearby.block" }
    struct TokenKey: CodableAttributedStringKey { typealias Value = String; static let name = "dearby.token" }
}
extension AttributeScopes {
    struct DearbyTemplateAttributes: AttributeScope {
        let block: TemplateAttributes.BlockKey
        let token: TemplateAttributes.TokenKey
        let swiftUI: SwiftUIAttributes
    }
    var dearbyTemplate: DearbyTemplateAttributes.Type { DearbyTemplateAttributes.self }
}
extension AttributeDynamicLookup {
    subscript<T: AttributedStringKey>(dynamicMember keyPath: KeyPath<AttributeScopes.DearbyTemplateAttributes, T>) -> T { self[T.self] }
}

@available(iOS 26.0, *)
private struct RichTemplateEditor: View {
    @Binding var template: String
    let tokens: [TemplateDocument.Token]
    @State private var text = AttributedString()
    @State private var rendered = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            TextEditor(text: $text).font(.dearby(.body)).frame(minHeight: 200)
                .padding(8).background(DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 10)).accessibilityLabel("명함 본문")
                .onChange(of: text) { _, next in sync(next) }
                .onChange(of: template) { _, next in if next != rendered { render(next) } }
                .onAppear { render(template) }
            Text("제목·굵게·목록은 그대로 보이고, 데이터 칩은 고칠 수 없어요.").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
        }
    }
    /// Markdown → editor text (tokens become their values with the token attribute).
    private func render(_ source: String) {
        text = TemplateRendering.attributed(source, tokens: tokens)
        rendered = source
    }
    /// Editor text → markdown. If a locked token run was changed, the text is rebuilt so the token stays whole.
    private func sync(_ next: AttributedString) {
        let (markdown, damaged) = TemplateRendering.markdown(from: next, tokens: tokens)
        if damaged { render(markdown) } else { rendered = markdown }
        if template != markdown { template = markdown }
    }
}

/// Conversions between the template markdown and the rich editor's attributed text (subset of contract "템플릿").
enum TemplateRendering {
    static func attributed(_ source: String, tokens: [TemplateDocument.Token]) -> AttributedString {
        var result = AttributedString()
        for (index, block) in MarkdownDocument.blocks(source).enumerated() {
            if index > 0 { result += AttributedString("\n") }
            switch block {
            case .heading(let level, let text):
                var line = inline(text, tokens: tokens)
                line.font = level == 1 ? .dearby(.title2).bold() : level == 2 ? .dearby(.title3).bold() : .dearby(.headline)
                line.block = "h\(level)"
                result += line
            case .list(let ordered, let items):
                for (itemIndex, item) in items.enumerated() {
                    if itemIndex > 0 { result += AttributedString("\n") }
                    var line = AttributedString(ordered ? "\(itemIndex + 1). " : "• ") + inline(item, tokens: tokens)
                    line.block = ordered ? "ol" : "ul"
                    result += line
                }
            case .paragraph(let lines):
                for (lineIndex, line) in lines.enumerated() {
                    if lineIndex > 0 { result += AttributedString("\n") }
                    var attributed = inline(line, tokens: tokens)
                    attributed.block = "p"
                    result += attributed
                }
            }
        }
        return result
    }
    /// Inline markdown with tokens shown as their values, marked by the token attribute (teal, non-editable by `sync`).
    private static func inline(_ text: String, tokens: [TemplateDocument.Token]) -> AttributedString {
        var result = AttributedString()
        var cursor = text.startIndex
        for match in TemplateDocument.matches(text) {
            result += MarkdownDocument.inline(String(text[cursor..<match.range.lowerBound]))
            let id = match.kind + (match.key.isEmpty ? "" : (match.kind == "contact" ? ":" : ".") + match.key)
            let token = tokens.first { $0.id.lowercased() == id.lowercased() }
            var chip = AttributedString(token?.value.isEmpty == false ? token!.value : "〔\(token?.label ?? id)〕")
            chip.token = id
            chip.foregroundColor = DearbyStyle.teal
            chip.inlinePresentationIntent = .stronglyEmphasized
            result += chip
            cursor = match.range.upperBound
        }
        result += MarkdownDocument.inline(String(text[cursor...]))
        return result
    }
    /// Editor text → markdown. `damaged` is true when a token run's text no longer matches its value.
    static func markdown(from text: AttributedString, tokens: [TemplateDocument.Token]) -> (String, damaged: Bool) {
        var damaged = false
        var lines: [String] = []
        var line = ""
        var block = "p"
        var itemNumber = 0
        func flush() {
            let prefix: String
            switch block {
            case "h1": prefix = "# "
            case "h2": prefix = "## "
            case "h3": prefix = "### "
            case "ul": prefix = "- "
            case "ol": itemNumber += 1; prefix = "\(itemNumber). "
            default: prefix = ""; itemNumber = 0
            }
            var body = line
            if block == "ul" || block == "ol" { body = String(body.drop { $0 == "•" || $0 == " " || $0.isNumber || $0 == "." }) }
            lines.append(prefix + body)
            line = ""
        }
        for run in text.runs {
            let piece = String(text[run.range].characters)
            if let id = run.token {
                if let token = tokens.first(where: { $0.id.lowercased() == id.lowercased() }) {
                    let expected = token.value.isEmpty ? "〔\(token.label)〕" : token.value
                    if piece != expected { damaged = true }
                }
                line += "{{\(id)}}"
                continue
            }
            block = run.block ?? block
            let parts = piece.split(separator: "\n", omittingEmptySubsequences: false)
            for (index, part) in parts.enumerated() {
                if index > 0 { flush(); block = run.block ?? "p" }
                var segment = String(part)
                if let link = run.link { segment = "[\(segment)](\(link.absoluteString))" }
                if run.inlinePresentationIntent?.contains(.stronglyEmphasized) == true, !segment.isEmpty { segment = "**\(segment)**" }
                if run.inlinePresentationIntent?.contains(.emphasized) == true, !segment.isEmpty { segment = "*\(segment)*" }
                line += segment
            }
        }
        flush()
        return (lines.joined(separator: "\n"), damaged)
    }
}
