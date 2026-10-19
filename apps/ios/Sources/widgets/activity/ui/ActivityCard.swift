import SwiftUI

/// 발견 목록의 활동 카드. 본문을 누르면 `open`(상세 이동)을 부른다.
/// `applyURL`을 주면 카드 아래에 마감일 한 줄과 공식 신청 바로가기를 붙인다. 보일지 여부는 화면이 카탈로그 값으로 정한다.
/// 이미지(`imageURL`)가 없거나 못 불러오면 달력 아이콘과 조직 이름을 담은 타일을 보인다. 이미지·타일은 카드 글자와 같은 정보라 접근성에서 숨긴다.
struct ActivityCard: View {
    let activityID: String
    let title: String
    let organization: String
    var imageURL: URL?
    let summary: String
    let status: String
    let date: String
    var applyURL: URL?
    var recruitmentEnd: Date?
    /// Activity tags (contract "태그"); shown as quiet chips under the date.
    var tags: [Tag] = []
    let open: () -> Void
    var body: some View {
        VStack(spacing: 0) {
            Button(action: open) {
                HStack(alignment: .top, spacing: 12) {
                    thumbnail.frame(width: 104, height: 112).clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 9)).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 7) {
                        Text(title).font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink)
                        Text(summary).font(.dearby(.caption)).lineLimit(2)
                        Divider()
                        Label(status, systemImage: "person.badge.clock")
                        Label(date, systemImage: "calendar")
                        TagChips(tags: tags, limit: 3)
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
    private var thumbnail: some View {
        AsyncImage(url: imageURL) { phase in
            if let image = phase.image { image.resizable().scaledToFill() } else { tile }
        }
    }
    private var tile: some View {
        VStack(spacing: 8) {
            Image(systemName: "calendar")
                .font(.dearby(.title2)).foregroundStyle(DearbyStyle.teal)
            Text(organization).font(.dearby(.caption2).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                .multilineTextAlignment(.center).lineLimit(2).minimumScaleFactor(0.8).padding(.horizontal, 8)
        }.frame(maxWidth: .infinity, maxHeight: .infinity).background(DearbyStyle.mint)
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
/// 미리보기 예시. 주소는 실제가 아닌 example.invalid 값이다.
struct ActivityCardSample: View {
    var body: some View {
        VStack(spacing: 12) {
            ActivityCard(activityID: "1", title: "Dearby 개발자 컨퍼런스", organization: "Dearby",
                         summary: "개발자와 기획자가 함께 만드는 하루", status: "모집 중", date: "2026-10-24",
                         applyURL: URL(string: "https://example.invalid/apply"),
                         recruitmentEnd: ISO8601DateFormatter().date(from: "2026-10-20T14:59:00Z"),
                         tags: [Tag(id: 1, name: "네트워킹"), Tag(id: 2, name: "개발지식")]) {}
            ActivityCard(activityID: "2", title: "주말 데이터 분석 스터디", organization: "한국데이터산업진흥원",
                         summary: "선착순으로 모여요", status: "모집 마감", date: "11월 15일") {}
        }.padding(20).background(.white)
    }
}

#Preview("활동 카드") { ActivityCardSample() }
#endif
