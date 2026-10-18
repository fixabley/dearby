import SwiftUI

/// 발견 아코디언의 프로그램 머리글(contract "발견 프로그램 묶음"): 이름·조직·열린 활동 수·가장 가까운 마감. 누르면 `toggle`.
/// 접근성에서는 `펼침`/`접힘` 상태를 가진 버튼으로 읽히고, 동작 줄이기에서는 화살표가 회전하지 않는다.
struct ProgramHeader: View {
    let name: String
    let organization: String
    let openCount: Int
    var nearestDeadline: Date?
    let expanded: Bool
    let toggle: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        Button(action: toggle) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(name).font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink).multilineTextAlignment(.leading)
                    Text(organization).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                    HStack(spacing: 8) {
                        Text("열린 활동 \(openCount)개").font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                            .padding(.horizontal, 8).padding(.vertical, 2).background(DearbyStyle.mint, in: Capsule())
                        if let deadline { Text(deadline).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet) }
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.down").font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                    .rotationEffect(.degrees(expanded ? 0 : -90))
                    .animation(reduceMotion ? nil : .snappy, value: expanded)
            }.padding(14).frame(minHeight: 44).contentShape(Rectangle())
                .background(expanded ? DearbyStyle.mint : .white, in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(expanded ? DearbyStyle.teal : DearbyStyle.line))
        }.buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel([name, organization, "열린 활동 \(openCount)개", deadline].compactMap { $0 }.joined(separator: ", "))
            .accessibilityValue(expanded ? "펼침" : "접힘").accessibilityHint(expanded ? "누르면 접어요" : "누르면 열린 활동을 보여요")
            .accessibilityIdentifier("program-\(name)")
    }
    private var deadline: String? {
        guard let nearestDeadline else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "M월 d일 (E)"
        return formatter.string(from: nearestDeadline) + " 마감"
    }
}

#if DEBUG
#Preview("프로그램 머리글") {
    VStack(spacing: 12) {
        ProgramHeader(name: "부스트캠프", organization: "네이버 커넥트재단", openCount: 2,
                      nearestDeadline: ISO8601DateFormatter().date(from: "2026-10-30T14:59:00Z"), expanded: true) {}
        ProgramHeader(name: "Let'Swift", organization: "Let'Swift", openCount: 1, expanded: false) {}
    }.padding(20).background(.white)
}
#endif
