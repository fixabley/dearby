import SwiftUI

/// 발견 프로그램 카드의 머리(contract "발견 카드 리뉴얼" 2026-10-19, iOS 네이티브 관용구 — AGENTS.md 2026-10-19 "플랫폼별 네이티브 디자인"):
/// 왼쪽 로고 자리(44, 모서리 14: `logoURL`을 `AsyncImage`로 꽉 채워 보이고 로딩 중·실패·없음은 이름 첫 글자 아바타 — contract "프로그램 로고"), 가운데 이름·조직·태그 칩, 오른쪽 모집 중 + 모집 예정 활동 수(`.borderedProminent` 캡슐 배지)와
/// 보조 글자(`M월 d일 마감` / `M월 d일 시작` / `최근 yyyy.M`). 펼치면 배지가 `.bordered` 원형 버튼 안의 SF Symbol `chevron.down`(180° 회전, `접기`)으로 바뀐다.
/// 머리 어디를 눌러도 `toggle`. 접근성에서는 `펼침`/`접힘` 상태를 가진 버튼으로 읽히고, 동작 줄이기에서는 배지↔화살표가 즉시 바뀐다.
struct ProgramHeader: View {
    let name: String
    let organization: String
    /// The program logo, resolved against the API origin; nil without one.
    var logoURL: URL?
    let openCount: Int
    var nearestDeadline: Date?
    /// Activities whose recruitment has not started yet; counted in the badge, and the meta line says `M월 d일 시작` when nothing is recruiting.
    var upcomingCount: Int = 0
    var nearestStart: Date?
    /// The program's most recent activity start; `최근 yyyy.M` when nothing is recruiting or upcoming.
    var latestActivity: Date?
    /// Program tags (contract "태그"): at most three chips and `+n`.
    var tags: [Tag] = []
    let expanded: Bool
    let toggle: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Button(action: toggle) {
                HStack(alignment: .top, spacing: 12) {
                    logo
                    VStack(alignment: .leading, spacing: 2) {
                        Text(name).font(.dearby(.headline)).foregroundStyle(.primary).multilineTextAlignment(.leading)
                        Text(organization).font(.dearby(.subheadline)).foregroundStyle(.secondary)
                        TagChips(tags: tags, limit: 3).padding(.top, 6)
                    }
                    Spacer(minLength: 0)
                }.contentShape(Rectangle())
            }.buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel([name, organization, countLabel, meta, tags.isEmpty ? nil : "태그 " + tags.map(\.name).joined(separator: ", ")]
                    .compactMap { $0 }.joined(separator: ", "))
                .accessibilityValue(expanded ? "펼침" : "접힘").accessibilityHint(expanded ? "누르면 접어요" : "누르면 활동을 보여요")
                .accessibilityIdentifier("program-\(name)")
            VStack(alignment: .trailing, spacing: 6) {
                control.frame(minHeight: 34)
                    .animation(reduceMotion ? nil : .spring(response: 0.35, dampingFraction: 0.85), value: expanded)
                if let meta { Text(meta).font(.dearby(.caption2)).foregroundStyle(.secondary).lineLimit(1) }
            }
        }.padding(EdgeInsets(top: 14, leading: 16, bottom: 12, trailing: 16))
    }
    /// The 44-point logo slot: the image filled and clipped to a 14-point continuous corner; the initial avatar until it loads, on failure or without a URL.
    @ViewBuilder private var logo: some View {
        if let logoURL {
            AsyncImage(url: logoURL) { phase in
                if let image = phase.image { image.resizable().scaledToFill() } else { initial }
            }
            .frame(width: 44, height: 44).clipShape(.rect(cornerRadius: 14, style: .continuous))
        } else {
            initial
        }
    }
    private var initial: some View {
        Text(String(name.prefix(1))).font(.dearby(fixedSize: 18, weight: .bold)).foregroundStyle(DearbyStyle.teal)
            .frame(width: 44, height: 44).background(DearbyStyle.mint, in: .rect(cornerRadius: 14, style: .continuous))
    }
    /// Collapsed: the count as a prominent capsule badge. Expanded: a bordered circular chevron pointing up (`chevron.down` rotated 180°).
    @ViewBuilder private var control: some View {
        if expanded {
            Button(action: toggle) {
                Image(systemName: "chevron.down").font(.dearby(fixedSize: 14, weight: .semibold)).frame(width: 18, height: 18)
                    .rotationEffect(.degrees(180))
            }
            .buttonStyle(.bordered).buttonBorderShape(.circle).tint(DearbyStyle.teal)
            .accessibilityLabel("접기").accessibilityHint("누르면 접어요")
            .accessibilityIdentifier("program-toggle-\(name)")
            .transition(reduceMotion ? .identity : .scale(scale: 0.6).combined(with: .opacity))
        } else {
            Button(action: toggle) {
                Text("\(openCount + upcomingCount)").font(.dearby(.subheadline).weight(.semibold)).monospacedDigit()
                    .frame(minWidth: 18, minHeight: 18)
            }
            .buttonStyle(.borderedProminent).buttonBorderShape(.capsule).tint(DearbyStyle.teal)
            .accessibilityLabel(countLabel ?? "활동 없음").accessibilityHint("누르면 활동을 보여요")
            .accessibilityIdentifier("program-toggle-\(name)")
            .transition(reduceMotion ? .identity : .scale(scale: 0.6).combined(with: .opacity))
        }
    }
    /// Spoken count: the badge's number with its meaning.
    private var countLabel: String? {
        let total = openCount + upcomingCount
        guard total > 0 else { return nil }
        if openCount > 0 && upcomingCount > 0 { return "모집 중 \(openCount)개, 모집 예정 \(upcomingCount)개" }
        return openCount > 0 ? "모집 중 \(openCount)개" : "모집 예정 \(upcomingCount)개"
    }
    /// The meta line under the badge (contract): nearest deadline while recruiting, nearest start when only upcoming, else the latest activity month.
    private var meta: String? {
        if openCount > 0, let nearestDeadline { return Self.format(nearestDeadline, "M월 d일") + " 마감" }
        if openCount == 0, upcomingCount > 0, let nearestStart { return Self.format(nearestStart, "M월 d일") + " 시작" }
        if openCount == 0, upcomingCount == 0, let latestActivity { return "최근 " + Self.format(latestActivity, "yyyy.M") }
        return nil
    }
    private static func format(_ date: Date, _ pattern: String) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = pattern
        return formatter.string(from: date)
    }
}

#if DEBUG
#Preview("프로그램 머리") {
    VStack(spacing: 10) {
        ProgramHeader(name: "부스트캠프", organization: "네이버 커넥트재단", openCount: 2,
                      nearestDeadline: ISO8601DateFormatter().date(from: "2026-11-10T09:00:00Z"),
                      tags: [Tag(id: 1, name: "개발지식"), Tag(id: 2, name: "프로젝트 개발"), Tag(id: 3, name: "협업"), Tag(id: 4, name: "멘토링")], expanded: true) {}
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
        ProgramHeader(name: "SK AIGEN", organization: "SK", openCount: 0, upcomingCount: 1,
                      nearestStart: ISO8601DateFormatter().date(from: "2026-10-31T15:00:00Z"), tags: [Tag(id: 5, name: "창업")], expanded: false) {}
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
        ProgramHeader(name: "Let'Swift", organization: "Let'Swift", openCount: 0,
                      latestActivity: ISO8601DateFormatter().date(from: "2025-10-08T01:00:00Z"), expanded: false) {}
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
    }.padding(16).background(Color(.systemGroupedBackground))
}
#endif
