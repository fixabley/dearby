import SwiftUI

/// 받은 명함 목록 한 줄. 화면이 명함 모델을 이름·직무·첫 활동 값으로 바꿔 넘기고, 누르면 `open`으로 상세를 연다.
struct ReceivedCardRow: View {
    let name: String
    let job: String
    var activityTitle: String?
    var otherActivityCount = 0
    let open: () -> Void
    var body: some View {
        Button(action: open) {
            HStack(spacing: 12) {
                DearbyAvatar(name: name, size: 44)
                VStack(alignment: .leading, spacing: 3) {
                    Text(name).font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink)
                    if !job.isEmpty { Text(job).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).lineLimit(1) }
                    if let activityTitle { DearbyTogetherActivityLabel(title: activityTitle, otherCount: otherActivityCount) }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                    .accessibilityHidden(true)
            }.padding(.vertical, 10).frame(minHeight: 44).contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityElement(children: .combine)
    }
}

#if DEBUG
/// 미리보기와 캡처 테스트가 함께 쓰는 받은 명함 묶음 예시.
struct ReceivedCardGroupsSample: View {
    @State var open = true
    var body: some View {
        VStack(spacing: 0) {
            DearbySectionHeader(title: "Dearby 개발자 컨퍼런스", count: 2, expanded: open) { open.toggle() }
            if open {
                ReceivedCardRow(name: "이서연", job: "프로덕트 디자이너", activityTitle: "Dearby 개발자 컨퍼런스", otherActivityCount: 1) {}
                Divider()
                ReceivedCardRow(name: "박준호", job: "백엔드 개발", activityTitle: "Dearby 개발자 컨퍼런스") {}
            }
            DearbySectionHeader(title: "활동 없음", count: 1, expanded: true)
            ReceivedCardRow(name: "최유나", job: "커뮤니티 매니저") {}
        }.padding(20).background(.white)
    }
}

#Preview("받은 명함 묶음") { ReceivedCardGroupsSample() }
#endif
