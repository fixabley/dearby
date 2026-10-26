import SwiftUI

/// 펼친 프로그램 안의 활동 카드(contract "발견 카드 리뉴얼" 2026-10-19, iOS 네이티브 관용구). 로고 없이 왼쪽 위 활동명, 오른쪽 위 활동 상태 칩
/// (contract "활동 상태" 2026-10-26: `모집 중` 청록 채움 캡슐 / `모집 예정` 청록 틴트 캡슐 / `진행 중` 시스템 파랑 틴트 / `모집 마감`·`종료` 시스템 fill 캡슐;
/// `모집 정보 없음`은 칩 없이 `모집` 줄이 `모집 일정 미정`; 모집 예정에 모집 시작이 있으면 칩 왼쪽에 `시작 D-n` 민트 미니 배지), 아래 `LabeledContent` 세 줄(`모집`·`활동`·`장소`; 장소는 있을 때만).
/// 모집 중·모집 예정이면 `모집` 줄의 값이 청록 굵은 글씨에 옅은 민트 바탕으로 강조된다. 카드를 누르면 `open`(상세 이동). 접근성에서는 상태와 세 줄을 한 라벨로 읽는다.
struct ActivityCard<Accessory: View>: View {
    let activityID: String
    let title: String
    /// The server's 활동 상태 (`ActivityModel.status(at:)`); nil shows no chip.
    let status: ActivityStatus?
    /// Recruitment window text (`ActivityModel.recruitmentDate`: `M.d – M.d` with both bounds, else structured dates before the collected
    /// label); nil, or 모집 정보 없음, reads as `모집 일정 미정`.
    var recruitmentText: String?
    /// `D-3`/`D-DAY` beside the period while recruiting (contract "모집 날짜 표기 — 2026-10-26"); nil for none.
    var recruitmentDDay: String?
    /// `시작 D-5`/`오늘 시작` beside the `모집 예정` chip (`ActivityModel.upcomingStartDDay`, contract "모집 예정 시작 D-day — 2026-10-26"); nil for none.
    var startDDay: String?
    /// Activity period text (`ActivityModel.scheduleText`); nil reads as `미정`.
    var scheduleText: String?
    /// Place name (contract "활동 장소"); nil omits the row.
    var place: String?
    /// The chip line under the rows (contract "태그 아이콘+라벨·분야 1개·자동 펼침 없음" 2026-10-24): the first 분야 tag, then 성격; none omits it.
    var fieldTag: Tag?
    var traitTags: [Tag] = []
    /// The card's fill: the grouped background inside a discovery card, the secondary one on its own (즐겨찾기).
    var surface = Color(.systemGroupedBackground)
    /// A block has a 트랙·세션 시간표 (contract "트랙·세션 시간표"): a `시간표` badge beside the status.
    var hasTimetable = false
    /// The heart, bell and `+` (`FavoriteControls`) at the bottom trailing corner, outside the card's button; `EmptyView` for none.
    @ViewBuilder var accessory: Accessory
    let open: () -> Void
    private var hasAccessory: Bool { Accessory.self != EmptyView.self }
    /// The accessory's measured width: the chip line keeps clear of it.
    @State private var accessoryWidth: CGFloat = 0
    var body: some View {
        Button(action: open) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .top, spacing: 10) {
                    Text(title).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(.primary)
                        .multilineTextAlignment(.leading).frame(maxWidth: .infinity, alignment: .leading)
                    if hasTimetable { DearbyMiniBadge(title: "시간표", systemImage: "tablecells") }
                    // 시작 D-n left of the 모집 예정 chip (contract "모집 예정 시작 D-day"; the detail puts it right of the status badge).
                    if let startDDay { DearbyMiniBadge(title: startDDay) }
                    if let badge = status?.badge { statusChip(badge) }
                }
                VStack(alignment: .leading, spacing: 3) {
                    LabeledContent("모집") {
                        HStack(alignment: .firstTextBaseline, spacing: 6) {
                            Text(recruitmentValue)
                            if let recruitmentDDay { DearbyMiniBadge(title: recruitmentDDay, filled: true) }
                        }
                    }.labeledContentStyle(DiscoveryRowStyle(emphasized: emphasized))
                    LabeledContent("활동") { Text(scheduleText ?? "미정") }.labeledContentStyle(DiscoveryRowStyle())
                    if let place, !place.isEmpty { LabeledContent("장소") { Text(place) }.labeledContentStyle(DiscoveryRowStyle()) }
                }
                TagChipLine(field: fieldTag, traits: traitTags).padding(.trailing, hasAccessory ? accessoryWidth : 0)
                    .frame(minHeight: hasAccessory ? 32 : nil, alignment: .leading)
            }
            .padding(EdgeInsets(top: 12, leading: 12, bottom: 10, trailing: 12)).frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }.buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel([title, status?.badge, startDDay, hasTimetable ? "시간표 있음" : nil, "모집 " + recruitmentValue + (recruitmentDDay.map { " " + $0 } ?? ""), "활동 " + (scheduleText ?? "미정"),
                                 place.map { "장소 " + $0 }, TagChipLine.label(field: fieldTag, traits: traitTags)].compactMap { $0 }.joined(separator: ", "))
            .accessibilityHint("누르면 활동 상세를 보여요")
            .accessibilityIdentifier("activity-\(activityID)")
            .overlay(alignment: .bottomTrailing) {
                accessory.padding(.trailing, 4).padding(.bottom, 2)
                    .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { accessoryWidth = $0 }
            }
            .background(surface, in: .rect(cornerRadius: 14, style: .continuous))
    }
    /// Recruiting or upcoming: the recruitment row stands out (contract 강조 규칙).
    private var emphasized: Bool { status?.applicable ?? false }
    private var recruitmentValue: String { status == .noRecruitment ? "모집 일정 미정" : recruitmentText ?? "모집 일정 미정" }
    /// Capsule tints as iOS uses them: filled for the active state, tinted for the pending and running ones (system blue for 진행 중),
    /// system fill for the closed ones. Semantic colours, so light and dark both follow the system.
    private func statusChip(_ title: String) -> some View {
        let (foreground, background): (Color, Color) = switch status {
        case .recruiting: (DearbyStyle.onTeal, DearbyStyle.tealFill)
        case .recruitingUpcoming: (DearbyStyle.teal, DearbyStyle.mint)
        case .inProgress: (.blue, .blue.opacity(0.15))
        default: (.secondary, Color(.tertiarySystemFill))
        }
        return Text(title).font(.dearby(.caption2).weight(.semibold)).foregroundStyle(foreground)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(background, in: Capsule())
    }
}

/// `LabeledContent` as a leading label column and a leading, wrapping value (the trailing default suits settings rows, not period text).
/// `emphasized` gives the value teal semibold type on a mint rounded background (contract 강조 규칙).
struct DiscoveryRowStyle: LabeledContentStyle {
    var emphasized = false
    func makeBody(configuration: Configuration) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            configuration.label.font(.dearby(.footnote)).foregroundStyle(.secondary).frame(width: 36, alignment: .leading)
            configuration.content.font(.dearby(.footnote).weight(emphasized ? .semibold : .regular))
                .foregroundStyle(emphasized ? DearbyStyle.teal : .primary).multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, emphasized ? 6 : 0).padding(.vertical, emphasized ? 3 : 0)
        .background(emphasized ? DearbyStyle.mint : .clear, in: .rect(cornerRadius: 8, style: .continuous))
        .padding(.horizontal, emphasized ? -6 : 0)
    }
}

#if DEBUG
/// 미리보기 예시.
struct ActivityCardSample: View {
    var body: some View {
        VStack(spacing: 8) {
            ActivityCard(activityID: "1", title: "부스트캠프 Web·Mobile 10기", status: .recruiting,
                         recruitmentText: "10.20 – 11.10", recruitmentDDay: "D-3", scheduleText: "2026년 11월 24일 (화) ~ 2027년 2월 6일 (토)", place: "온라인 · 서울 강남",
                         fieldTag: Tag(id: 10, name: "웹·앱", nameEn: "webApp", kind: .field),
                         traitTags: [Tag(id: 2, name: "프로젝트", nameEn: "project"), Tag(id: 4, name: "멘토링", nameEn: "mentoring")],
                         accessory: { EmptyView() }) {}
            ActivityCard(activityID: "2", title: "SK AIGEN 1기", status: .recruitingUpcoming,
                         recruitmentText: "11.1 – 11.15", startDDay: "시작 D-5", scheduleText: "2027년 3월 11일부터", place: "SK 서린빌딩", accessory: { EmptyView() }) {}
            ActivityCard(activityID: "3", title: "우아콘 2025", status: .ended, scheduleText: "2025년 10월 30일 (목)", accessory: { EmptyView() }) {}
        }.padding(10).background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
            .padding(16).background(Color(.systemGroupedBackground))
    }
}

#Preview("활동 카드") { ActivityCardSample() }
#endif
