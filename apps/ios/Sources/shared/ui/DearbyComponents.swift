import SwiftUI

struct DearbyBadge: View {
    let title: String
    /// Danger colouring for a negative mark such as `탈락`.
    var danger = false
    /// Quiet colouring for a neutral notice such as `3일 뒤 사라져요`.
    var quiet = false
    var body: some View {
        Text(title).font(.dearby(.caption).weight(.semibold)).foregroundStyle(danger ? DearbyStyle.danger : quiet ? DearbyStyle.quiet : DearbyStyle.teal)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(danger ? DearbyStyle.danger.opacity(0.1) : quiet ? DearbyStyle.muted : DearbyStyle.mint, in: Capsule())
    }
}
/// The small capsule beside a card title or a date (`시간표`, `D-3`): caption2 semibold teal on mint, with an optional SF Symbol;
/// `filled` is white on the teal fill (a countdown, readable on a mint row too).
struct DearbyMiniBadge: View {
    let title: String
    var systemImage: String?
    var filled = false
    var body: some View {
        Group {
            if let systemImage { Label(title, systemImage: systemImage) } else { Text(title) }
        }
        .font(.dearby(.caption2).weight(.semibold)).monospacedDigit().foregroundStyle(filled ? DearbyStyle.onTeal : DearbyStyle.teal)
        .padding(.horizontal, 8).padding(.vertical, 3).background(filled ? DearbyStyle.tealFill : DearbyStyle.mint, in: Capsule())
    }
}
struct DearbyAvatar: View {
    let name: String
    var filled = false
    var size: CGFloat = 76
    var body: some View {
        Text(String(name.prefix(1))).font(.dearby(fixedSize: size * 0.42, weight: .bold))
            .foregroundStyle(filled ? DearbyStyle.onTeal : DearbyStyle.teal).frame(width: size, height: size)
            .background(filled ? DearbyStyle.tealFill : DearbyStyle.mint, in: Circle())
            .accessibilityHidden(true)
    }
}
struct DearbyInfoRow: View {
    let title: String
    let value: String
    var symbol: String?
    @Environment(\.dynamicTypeSize) private var size
    var body: some View {
        let layout = size.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 6))
            : AnyLayout(HStackLayout(alignment: .top, spacing: 14))
        layout {
            HStack(spacing: 10) {
                // 행 앞 글리프(contract "아이콘 통일"): 24, 보조색, 같은 열.
                if let symbol { Image(systemName: symbol).font(.dearby(.title3)).frame(width: 24) }
                Text(title)
            }.foregroundStyle(DearbyStyle.quiet).frame(minWidth: 92, alignment: .leading)
            Text(value).frame(maxWidth: .infinity, alignment: .leading)
        }.font(.dearby(.subheadline)).padding(.vertical, 4)
    }
}
struct DearbySheetHeader: View {
    let title: String
    var detail = ""
    let close: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            Text(title).font(.dearby(.title2).bold())
            Spacer()
            if !detail.isEmpty { Text(detail).font(.dearby(.subheadline)).foregroundStyle(.secondary) }
            Button(action: close) {
                Image(systemName: "xmark").font(.dearby(.title3)).foregroundStyle(DearbyStyle.quiet).frame(width: 44, height: 44)
            }.accessibilityLabel("닫기")
        }.padding(.leading, 20).padding(.trailing, 8).padding(.top, 20)
    }
}

/// Wraps chips onto new lines (iOS 16 `Layout`); `centered` centers each line (card header), otherwise lines start at the leading edge.
struct ChipFlow: Layout {
    var spacing: CGFloat = 8
    var centered = false
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        let lines = lines(in: width, subviews: subviews)
        return CGSize(width: width, height: lines.reduce(0) { $0 + $1.height } + spacing * CGFloat(max(lines.count - 1, 0)))
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for line in lines(in: bounds.width, subviews: subviews) {
            var x = bounds.minX + (centered ? (bounds.width - line.width) / 2 : 0)
            for index in line.indices {
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: .unspecified)
                x += subviews[index].sizeThatFits(.unspecified).width + spacing
            }
            y += line.height + spacing
        }
    }
    private struct Line { var indices: [Int] = []; var width: CGFloat = 0; var height: CGFloat = 0 }
    private func lines(in width: CGFloat, subviews: Subviews) -> [Line] {
        var lines = [Line()]
        for (index, view) in subviews.enumerated() {
            let size = view.sizeThatFits(.unspecified)
            var line = lines.removeLast()
            if !line.indices.isEmpty && line.width + spacing + size.width > width { lines.append(line); line = Line() }
            line.width += (line.indices.isEmpty ? 0 : spacing) + size.width
            line.height = max(line.height, size.height)
            line.indices.append(index)
            lines.append(line)
        }
        return lines
    }
}
