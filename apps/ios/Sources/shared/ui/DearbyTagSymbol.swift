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
    ]
}

/// A tag as icon and text in a quiet capsule (활동 상세의 분야·얻을 수 있는 것 칩).
struct DearbyTagChip: View {
    let title: String
    let nameEn: String?
    var body: some View {
        Label(title, systemImage: DearbyTagSymbol.name(nameEn))
            .font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(DearbyStyle.muted, in: Capsule())
    }
}
