import SwiftUI

/// SF Symbol for a tag's `nameEn` (contract "분야·성격 태그와 아이콘" 2026-10-24). An unknown or missing name (an operator's new tag) is `tag`.
enum DearbyTagSymbol {
    static func name(_ nameEn: String?) -> String { nameEn.flatMap { symbols[$0] } ?? "tag" }
    private static let symbols: [String: String] = [
        "startup": "lightbulb", "project": "hammer", "mentoring": "bubble.left.and.bubble.right",
        "networking": "point.3.connected.trianglepath.dotted", "funding": "wonsign.circle",
        "it": "laptopcomputer", "dataAi": "sparkles", "cloud": "cloud", "semiconductor": "cpu", "mechanical": "gearshape.2",
        "civilEngineering": "building.2", "engineering": "wrench.and.screwdriver", "security": "lock.shield", "business": "chart.bar", "marketing": "megaphone",
        "investing": "chart.line.uptrend.xyaxis", "volunteering": "heart", "reading": "book", "video": "video",
        "webApp": "apps.iphone", "game": "gamecontroller", "openSource": "chevron.left.forwardslash.chevron.right",
    ]
}

/// A tag as icon and text in a capsule: quiet (활동 상세의 분야·얻을 수 있는 것, 발견 카드의 성격) or `accent` (발견 카드의 분야: 민트 바탕·청록 글자).
struct DearbyTagChip: View {
    let title: String
    let nameEn: String?
    var accent = false
    var body: some View {
        Label(title, systemImage: DearbyTagSymbol.name(nameEn)).dearbyTagChip(accent: accent)
    }
}

extension View {
    /// The tag chip's capsule look, also for a chip line's `+n`.
    func dearbyTagChip(accent: Bool = false) -> some View {
        font(.dearby(.caption).weight(.semibold)).foregroundStyle(accent ? DearbyStyle.teal : DearbyStyle.quiet).lineLimit(1)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(accent ? DearbyStyle.mint : DearbyStyle.muted, in: Capsule())
    }
}

/// A multi-select choice as icon and text (추천 퍼널의 분야 칩): picked is filled teal with white text, else the system fill; at least 44 high.
/// Spoken as a toggle with its state.
struct DearbyToggleChip: View {
    let title: String
    let systemImage: String
    @Binding var isOn: Bool
    var body: some View {
        Button { isOn.toggle() } label: {
            HStack(spacing: 6) {
                Image(systemName: isOn ? "checkmark" : systemImage)
                Text(title)
            }
                .font(.dearby(.body).weight(.semibold)).lineLimit(1).fixedSize().foregroundStyle(isOn ? DearbyStyle.onTeal : DearbyStyle.ink)
                .padding(.horizontal, 16).frame(minHeight: 44)
                .background(isOn ? DearbyStyle.tealFill : Color(.secondarySystemGroupedBackground), in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title).accessibilityAddTraits(.isToggle).accessibilityValue(isOn ? "켬" : "끔")
    }
}
