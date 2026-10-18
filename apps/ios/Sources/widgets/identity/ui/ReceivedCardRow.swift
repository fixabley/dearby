import SwiftUI

/// 받은 명함 목록 한 줄(contract "받은 명함 목록 표시"): 이니셜 아바타 · 이름 · 직무, 소속이 있으면 민트 칩. 전체가 눌리는 한 줄이고 화살표는 없다.
/// 화면이 명함 모델을 값으로 바꿔 넘기고, 누르면 `open`으로 상세를 연다.
struct ReceivedCardRow: View {
    let name: String
    let job: String
    var organization: String?
    let open: () -> Void
    var body: some View {
        Button(action: open) {
            HStack(spacing: 12) {
                DearbyAvatar(name: name, size: 44)
                VStack(alignment: .leading, spacing: 4) {
                    Text(name).font(.dearby(.headline).weight(.bold)).foregroundStyle(DearbyStyle.ink)
                    HStack(spacing: 6) {
                        if !job.isEmpty { Text(job).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).lineLimit(1) }
                        if let organization, !organization.isEmpty {
                            Text(organization).font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.teal).lineLimit(1)
                                .padding(.horizontal, 8).padding(.vertical, 3).background(DearbyStyle.mint, in: Capsule())
                        }
                    }
                }
                Spacer(minLength: 0)
            }.padding(.vertical, 10).frame(minHeight: 44).contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityElement(children: .combine)
    }
}

#if DEBUG
/// 미리보기용 받은 명함 목록 예시.
struct ReceivedCardListSample: View {
    var body: some View {
        VStack(spacing: 0) {
            ReceivedCardRow(name: "이서연", job: "프로덕트 디자이너", organization: "Dearby") {}
            Divider()
            ReceivedCardRow(name: "박준호", job: "백엔드 개발") {}
            Divider()
            ReceivedCardRow(name: "최유나", job: "커뮤니티 매니저", organization: "Let'Swift 준비위원회") {}
        }.padding(20).background(.white)
    }
}

#Preview("받은 명함 목록") { ReceivedCardListSample() }
#endif
