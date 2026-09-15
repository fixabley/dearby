import SwiftUI

/// Field boundaries come from State; whitespace is the only inferred word boundary.
struct NoticeCardPlaceText: View {
    let text: String
    @ScaledMetric(relativeTo: .subheadline) private var wordSpacing = 4.0

    var body: some View {
        let words = text.split(whereSeparator: \.isWhitespace).map(String.init)
        NoticeCardWordLayout(spacing: wordSpacing) {
            ForEach(words.indices, id: \.self) { index in
                Text(words[index]).fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
    }
}

private struct NoticeCardWordLayout: Layout {
    let spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = max(1, proposal.width ?? subviews.reduce(0) { $0 + $1.sizeThatFits(.unspecified).width + spacing })
        let frames = frames(width: width, subviews: subviews)
        return CGSize(width: width, height: frames.map(\.maxY).max() ?? 0)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for (view, frame) in zip(subviews, frames(width: max(1, bounds.width), subviews: subviews)) {
            view.place(at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY),
                       anchor: .topLeading, proposal: ProposedViewSize(width: frame.width, height: frame.height))
        }
    }

    private func frames(width: CGFloat, subviews: Subviews) -> [CGRect] {
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        return subviews.map { view in
            // A token wider than the entire column may wrap internally to prevent overflow.
            let ideal = view.sizeThatFits(.unspecified)
            let size = view.sizeThatFits(ProposedViewSize(width: min(ideal.width, width), height: nil))
            if x > 0 && x + size.width > width {
                x = 0
                y += rowHeight + 3
                rowHeight = 0
            }
            let frame = CGRect(x: x, y: y, width: size.width, height: size.height)
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            return frame
        }
    }
}
