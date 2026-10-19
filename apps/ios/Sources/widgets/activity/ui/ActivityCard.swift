import SwiftUI

/// 펼친 프로그램 안의 활동 카드(contract "발견 카드 리뉴얼" 2026-10-19). 로고 없이 왼쪽 위 활동명, 오른쪽 위 모집 상태 칩(`모집 중` 청록 채움 /
/// `모집 예정` 민트 / `마감` 회색), 아래 라벨 + 값 세 줄(`모집`·`활동`·`장소`; 장소는 있을 때만). 모집이 지나지 않았으면 `모집` 줄의 값이 청록 굵은 글씨에
/// 옅은 민트 바탕으로 강조된다. 카드를 누르면 `open`(상세 이동). 접근성에서는 상태 칩과 세 줄을 한 라벨로 읽는다.
struct ActivityCard: View {
    let activityID: String
    let title: String
    let recruitment: ActivityModel.Recruitment
    /// Recruitment window text; empty reads as `미정`.
    var recruitmentLabel: String = ""
    /// Activity period text; empty reads as `미정`.
    var dateLabel: String = ""
    /// Place name (contract "활동 장소"); nil omits the row.
    var place: String?
    let open: () -> Void
    var body: some View {
        Button(action: open) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .top, spacing: 10) {
                    Text(title).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.ink)
                        .multilineTextAlignment(.leading).frame(maxWidth: .infinity, alignment: .leading)
                    statusChip
                }
                VStack(alignment: .leading, spacing: 3) {
                    row("모집", recruitmentLabel.isEmpty ? "미정" : recruitmentLabel, emphasized: emphasized)
                    row("활동", dateLabel.isEmpty ? "미정" : dateLabel)
                    if let place, !place.isEmpty { row("장소", place) }
                }
            }
            .padding(EdgeInsets(top: 12, leading: 12, bottom: 10, trailing: 12)).frame(maxWidth: .infinity)
            .background(DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 14)).contentShape(Rectangle())
        }.buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel([title, statusText, "모집 " + (recruitmentLabel.isEmpty ? "미정" : recruitmentLabel),
                                 "활동 " + (dateLabel.isEmpty ? "미정" : dateLabel), place.map { "장소 " + $0 }].compactMap { $0 }.joined(separator: ", "))
            .accessibilityHint("누르면 활동 상세를 보여요")
            .accessibilityIdentifier("activity-\(activityID)")
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
    private var statusChip: some View {
        Text(statusText).font(.dearby(.caption2).weight(.semibold))
            .foregroundStyle(recruitment == .open ? .white : recruitment == .upcoming ? DearbyStyle.teal : DearbyStyle.quiet)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(recruitment == .open ? DearbyStyle.teal : recruitment == .upcoming ? DearbyStyle.mint : DearbyStyle.line, in: Capsule())
    }
    private func row(_ label: String, _ value: String, emphasized: Bool = false) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(label).font(.dearby(.footnote).weight(.medium)).foregroundStyle(DearbyStyle.quiet).frame(width: 36, alignment: .leading)
            Text(value).font(.dearby(.footnote).weight(emphasized ? .semibold : .regular))
                .foregroundStyle(emphasized ? DearbyStyle.teal : DearbyStyle.ink).multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, emphasized ? 6 : 0).padding(.vertical, emphasized ? 3 : 0)
        .background(emphasized ? DearbyStyle.mint : .clear, in: RoundedRectangle(cornerRadius: 8))
        .padding(.horizontal, emphasized ? -6 : 0)
    }
}

#if DEBUG
/// 미리보기 예시.
struct ActivityCardSample: View {
    var body: some View {
        VStack(spacing: 8) {
            ActivityCard(activityID: "1", title: "부스트캠프 Web·Mobile 10기", recruitment: .open,
                         recruitmentLabel: "10월 20일 (월) 10:00 ~ 11월 10일 (월) 18:00", dateLabel: "2026년 11월 24일 ~ 2027년 2월 6일", place: "온라인 · 서울 강남") {}
            ActivityCard(activityID: "2", title: "SK AIGEN 1기", recruitment: .upcoming,
                         recruitmentLabel: "11월 1일 (일) ~ 11월 15일 (일)", dateLabel: "2027년 3월 11일부터", place: "SK 서린빌딩") {}
            ActivityCard(activityID: "3", title: "우아콘 2025", recruitment: .closed, dateLabel: "2025년 10월 30일 (목)") {}
        }.padding(10).background(.white, in: RoundedRectangle(cornerRadius: 20)).padding(16).background(DearbyStyle.muted)
    }
}

#Preview("활동 카드") { ActivityCardSample() }
#endif
