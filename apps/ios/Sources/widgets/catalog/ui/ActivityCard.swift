import SwiftUI

/// 발견 목록의 활동 카드. 본문을 누르면 `open`(상세 이동)을 부른다.
/// `applyURL`을 주면 카드 아래에 마감일 한 줄과 공식 신청 바로가기를 붙인다. 보일지 여부는 화면이 카탈로그 값으로 정한다.
struct ActivityCard: View {
    let activityID: String
    let title: String
    let summary: String
    let status: String
    let dateAndPlace: String
    var applyURL: URL?
    var recruitmentEnd: Date?
    let open: () -> Void
    var body: some View {
        VStack(spacing: 0) {
            Button(action: open) {
                HStack(alignment: .top, spacing: 12) {
                    ActivityArtwork(activityID: activityID).frame(width: 104, height: 112).clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 9))
                    VStack(alignment: .leading, spacing: 7) {
                        Text(title).font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink)
                        Text(summary).font(.dearby(.caption)).lineLimit(2)
                        Divider()
                        Label(status, systemImage: "calendar")
                        Label(dateAndPlace, systemImage: "mappin.and.ellipse")
                    }.font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet).frame(maxWidth: .infinity, alignment: .leading)
                }.padding(10).contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityIdentifier("activity-\(activityID)")
            if let applyURL {
                Divider()
                HStack(spacing: 12) {
                    Text(deadline).font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
                    Spacer(minLength: 0)
                    Link(destination: applyURL) {
                        Label("공식 사이트에서 신청", systemImage: "arrow.up.right").font(.dearby(.subheadline).weight(.semibold))
                            .padding(.horizontal, 14).frame(minHeight: 44).foregroundStyle(DearbyStyle.teal)
                            .overlay(RoundedRectangle(cornerRadius: 11).stroke(DearbyStyle.teal))
                    }.accessibilityLabel("\(title) 공식 사이트에서 신청").accessibilityHint("외부 브라우저로 열려요")
                        .accessibilityIdentifier("apply-\(activityID)")
                }.padding(.horizontal, 10).padding(.vertical, 8)
            }
        }
        .background(.white, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.line))
    }
    private var deadline: String {
        guard let recruitmentEnd else { return "마감일 미확인" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "M월 d일 (E)"
        return formatter.string(from: recruitmentEnd) + " 마감"
    }
}

#if DEBUG
/// 미리보기와 캡처 테스트가 함께 쓰는 예시. 신청 주소는 실제가 아닌 example.invalid 값이다.
struct ActivityCardSample: View {
    var body: some View {
        VStack(spacing: 12) {
            ActivityCard(activityID: "conference", title: "Dearby 개발자 컨퍼런스", summary: "개발자와 기획자가 함께 만드는 하루",
                         status: "모집 중 · 바로 신청", dateAndPlace: "10월 24일 · 서울", applyURL: URL(string: "https://example.invalid/apply"),
                         recruitmentEnd: ISO8601DateFormatter().date(from: "2026-10-20T14:59:00Z")) {}
            ActivityCard(activityID: "camp", title: "Dearby 메이커 캠프", summary: "선발형 · 지원서 검토 후 안내",
                         status: "모집 중 · 선발형", dateAndPlace: "11월 7일 · 부산") {}
        }.padding(20).background(.white)
    }
}

#Preview("빠른 신청") { ActivityCardSample() }
#endif
