import SwiftUI

/// 탭 첫 화면의 제목 줄(contract "탭 제목 통일·머리 간소화" 2026-10-19 밤, 기준은 내 명함 페이지): 왼쪽 정렬 `.title.bold()`,
/// 좌우 여백 20, 안전 영역 바로 아래 위 여백 8. 오른쪽 `accessory`에 작은 동작(버튼·배지)을 둘 수 있다.
/// 다섯 탭(발견·내 활동·내 명함·받은 명함·프로필)만 쓰고, 뒤로 가기가 있는 하위 화면은 inline 내비게이션 제목을 유지한다.
struct PageTitle<Accessory: View>: View {
    let title: String
    let accessory: Accessory
    init(_ title: String, @ViewBuilder accessory: () -> Accessory) {
        self.title = title
        self.accessory = accessory()
    }
    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            Text(title).font(.title.bold()).accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            accessory
        }.padding(.horizontal, 20).padding(.top, 8)
    }
}

extension PageTitle where Accessory == EmptyView {
    init(_ title: String) { self.init(title) { EmptyView() } }
}

#if DEBUG
#Preview("탭 제목") {
    VStack(alignment: .leading, spacing: 12) {
        PageTitle("내 명함")
        PageTitle("내 활동") { DearbyBadge(title: "예시") }
        Spacer()
    }.background(Color(.systemGroupedBackground))
}
#endif
