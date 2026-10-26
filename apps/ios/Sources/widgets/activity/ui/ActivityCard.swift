import SwiftUI

/// 펼친 프로그램 안의 활동 카드(contract "발견 카드 리뉴얼" 2026-10-19, iOS 네이티브 관용구). 로고 없이 왼쪽 위 활동명, 오른쪽 위 모집 상태 칩
/// (`모집 중` 청록 채움 캡슐 / `모집 예정` 청록 틴트 캡슐 / `마감` 시스템 fill 캡슐), 아래 `LabeledContent` 세 줄(`모집`·`활동`·`장소`; 장소는 있을 때만).
/// 모집이 지나지 않았으면 `모집` 줄의 값이 청록 굵은 글씨에 옅은 민트 바탕으로 강조된다. 카드를 누르면 `open`(상세 이동). 접근성에서는 상태와 세 줄을 한 라벨로 읽는다.
struct ActivityCard<Accessory: View>: View {
    let activityID: String
    let title: String
    let recruitment: ActivityModel.Recruitment
    /// Recruitment window text (`ActivityModel.recruitmentText`: structured dates before the collected label); nil reads as `미정`.
    var recruitmentText: String?
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
                    if hasTimetable {
                        Label("시간표", systemImage: "tablecells").font(.dearby(.caption2).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                            .padding(.horizontal, 8).padding(.vertical, 3).background(DearbyStyle.mint, in: Capsule())
                    }
                    statusChip
                }
                VStack(alignment: .leading, spacing: 3) {
                    LabeledContent("모집") { Text(recruitmentText ?? "미정") }.labeledContentStyle(DiscoveryRowStyle(emphasized: emphasized))
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
            .accessibilityLabel([title, statusText, hasTimetable ? "시간표 있음" : nil, "모집 " + (recruitmentText ?? "미정"), "활동 " + (scheduleText ?? "미정"),
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
    private var emphasized: Bool { recruitment == .open || recruitment == .upcoming }
    private var statusText: String {
        switch recruitment {
        case .open: "모집 중"
        case .upcoming: "모집 예정"
        case .closed: "마감"
        case .unknown: "확인 필요"
        }
    }
    /// Capsule tints as iOS uses them: filled for the active state, tinted for the pending one, system fill for the rest.
    private var statusChip: some View {
        Text(statusText).font(.dearby(.caption2).weight(.semibold))
            .foregroundStyle(recruitment == .open ? DearbyStyle.onTeal : recruitment == .upcoming ? DearbyStyle.teal : .secondary)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(recruitment == .open ? DearbyStyle.tealFill : recruitment == .upcoming ? DearbyStyle.mint : Color(.tertiarySystemFill),
                        in: Capsule())
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
            ActivityCard(activityID: "1", title: "부스트캠프 Web·Mobile 10기", recruitment: .open,
                         recruitmentText: "10월 20일 (화) 10:00 ~ 11월 10일 (화) 18:00", scheduleText: "2026년 11월 24일 (화) ~ 2027년 2월 6일 (토)", place: "온라인 · 서울 강남",
                         fieldTag: Tag(id: 10, name: "웹·앱", nameEn: "webApp", kind: .field),
                         traitTags: [Tag(id: 2, name: "프로젝트", nameEn: "project"), Tag(id: 4, name: "멘토링", nameEn: "mentoring")],
                         accessory: { EmptyView() }) {}
            ActivityCard(activityID: "2", title: "SK AIGEN 1기", recruitment: .upcoming,
                         recruitmentText: "11월 1일 (일) ~ 11월 15일 (일)", scheduleText: "2027년 3월 11일부터", place: "SK 서린빌딩", accessory: { EmptyView() }) {}
            ActivityCard(activityID: "3", title: "우아콘 2025", recruitment: .closed, scheduleText: "2025년 10월 30일 (목)", accessory: { EmptyView() }) {}
        }.padding(10).background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
            .padding(16).background(Color(.systemGroupedBackground))
    }
}

#Preview("활동 카드") { ActivityCardSample() }
#endif
