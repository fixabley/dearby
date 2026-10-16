import SwiftUI

/// Renders a card body (markdown subset) as stacked text: headings, lists and paragraphs with inline bold/italic/links.
struct DearbyMarkdownText: View {
    let source: String
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(MarkdownDocument.blocks(source).enumerated()), id: \.offset) { _, block in
                switch block {
                case .heading(let level, let text):
                    Text(MarkdownDocument.inline(text)).font(level == 1 ? .dearby(.title2).bold() : level == 2 ? .dearby(.title3).bold() : .dearby(.headline))
                case .list(let ordered, let items):
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                            HStack(alignment: .firstTextBaseline, spacing: 8) {
                                Text(ordered ? "\(index + 1)." : "•").font(.dearby(.body)).foregroundStyle(DearbyStyle.quiet).frame(minWidth: 16, alignment: .trailing)
                                Text(MarkdownDocument.inline(item)).font(.dearby(.body))
                            }
                        }
                    }
                case .paragraph(let lines):
                    Text(lines.map(MarkdownDocument.inline).reduce(AttributedString()) { $0 + ($0.characters.isEmpty ? $1 : AttributedString("\n") + $1) }).font(.dearby(.body))
                }
            }
        }.frame(maxWidth: .infinity, alignment: .leading).tint(DearbyStyle.teal)
    }
}
