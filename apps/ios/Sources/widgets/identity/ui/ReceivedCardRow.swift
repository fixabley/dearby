import SwiftUI

/// 받은 명함 목록 한 줄. 화면이 명함 모델을 이름·직무 값으로 바꿔 넘기고, 누르면 `open`으로 상세를 연다.
struct ReceivedCardRow: View {
    let name: String
    let job: String
    let open: () -> Void
    var body: some View {
        Button(action: open) {
            HStack(spacing: 12) {
                DearbyAvatar(name: name, size: 44)
                VStack(alignment: .leading, spacing: 3) {
                    Text(name).font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink)
                    if !job.isEmpty { Text(job).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).lineLimit(1) }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                    .accessibilityHidden(true)
            }.padding(.vertical, 10).frame(minHeight: 44).contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityElement(children: .combine)
    }
}

#if DEBUG
/// 미리보기용 받은 명함 목록 예시.
struct ReceivedCardListSample: View {
    var body: some View {
        VStack(spacing: 0) {
            ReceivedCardRow(name: "이서연", job: "프로덕트 디자이너") {}
            Divider()
            ReceivedCardRow(name: "박준호", job: "백엔드 개발") {}
            Divider()
            ReceivedCardRow(name: "최유나", job: "커뮤니티 매니저") {}
        }.padding(20).background(.white)
    }
}

#Preview("받은 명함 목록") { ReceivedCardListSample() }
#endif
