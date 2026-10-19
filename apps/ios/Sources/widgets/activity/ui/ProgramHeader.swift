import SwiftUI

/// 발견 아코디언의 프로그램 머리글(contract "발견 프로그램 묶음"): 이름·조직·모집 중 활동 수(있을 때)·가장 가까운 마감, 모집 중이 없고 모집 예정이 있으면
/// `모집 예정 n개 · M월 d일 시작`, 둘 다 없으면 최근 활동 시기(contract "마감 숨기기" 2026-10-19 밤). 누르면 `toggle`.
/// 접근성에서는 `펼침`/`접힘` 상태를 가진 버튼으로 읽히고, 동작 줄이기에서는 화살표가 회전하지 않는다.
struct ProgramHeader: View {
    let name: String
    let organization: String
    let openCount: Int
    var nearestDeadline: Date?
    /// Activities whose recruitment has not started yet; shown as `모집 예정 n개 · M월 d일 시작` when nothing is recruiting.
    var upcomingCount: Int = 0
    var nearestStart: Date?
    /// The program's most recent activity start; shown as `최근 활동 yyyy.M` when nothing is recruiting (contract "모집 중인 프로그램만 보기").
    var latestActivity: Date?
    /// Program tags (contract "태그"): at most three chips and `+n`.
    var tags: [Tag] = []
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
                        if openCount > 0 {
                            Text("모집 중 \(openCount)개").font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                                .padding(.horizontal, 8).padding(.vertical, 2).background(DearbyStyle.mint, in: Capsule())
                            if let deadline { Text(deadline).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet) }
                        } else if upcomingCount > 0 {
                            Text("모집 예정 \(upcomingCount)개").font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                                .padding(.horizontal, 8).padding(.vertical, 2).background(DearbyStyle.mint, in: Capsule())
                            if let start { Text(start).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet) }
                        } else if let latest {
                            Text(latest).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                        }
                    }
                    TagChips(tags: tags, limit: 3)
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
            .accessibilityLabel([name, organization, status, openCount > 0 ? deadline : upcomingCount > 0 ? start : latest,
                                 tags.isEmpty ? nil : "태그 " + tags.map(\.name).joined(separator: ", ")].compactMap { $0 }.joined(separator: ", "))
            .accessibilityValue(expanded ? "펼침" : "접힘").accessibilityHint(expanded ? "누르면 접어요" : "누르면 활동을 보여요")
            .accessibilityIdentifier("program-\(name)")
    }
    private var status: String? {
        if openCount > 0 { return "모집 중 \(openCount)개" }
        if upcomingCount > 0 { return "모집 예정 \(upcomingCount)개" }
        return nil
    }
    private var start: String? {
        guard let nearestStart else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "M월 d일 (E)"
        return formatter.string(from: nearestStart) + " 시작"
    }
    private var deadline: String? {
        guard let nearestDeadline else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "M월 d일 (E)"
        return formatter.string(from: nearestDeadline) + " 마감"
    }
    private var latest: String? {
        guard let latestActivity else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "yyyy.M"
        return "최근 활동 " + formatter.string(from: latestActivity)
    }
}

#if DEBUG
#Preview("프로그램 머리글") {
    VStack(spacing: 12) {
        ProgramHeader(name: "부스트캠프", organization: "네이버 커넥트재단", openCount: 2,
                      nearestDeadline: ISO8601DateFormatter().date(from: "2026-10-30T14:59:00Z"),
                      tags: [Tag(id: 1, name: "개발지식"), Tag(id: 2, name: "프로젝트 개발"), Tag(id: 3, name: "협업"), Tag(id: 4, name: "멘토링")], expanded: true) {}
        ProgramHeader(name: "SK AIGEN", organization: "SK", openCount: 0, upcomingCount: 1,
                      nearestStart: ISO8601DateFormatter().date(from: "2026-10-31T15:00:00Z"), expanded: false) {}
        ProgramHeader(name: "Let'Swift", organization: "Let'Swift", openCount: 0,
                      latestActivity: ISO8601DateFormatter().date(from: "2025-10-08T01:00:00Z"), expanded: false) {}
    }.padding(20).background(.white)
}
#endif
