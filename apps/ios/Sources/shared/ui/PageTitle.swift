import SwiftUI

/// 탭 첫 화면의 제목 줄(contract "탭 제목 통일·머리 간소화" 2026-10-19 밤, 기준은 내 명함 페이지): 왼쪽 정렬 Pretendard `.dearby(.title).bold()`(2026-10-19 조율 결정: 다른 제목과 같은 글꼴),
/// 좌우 여백 20, 안전 영역 바로 아래 위 여백 8. 오른쪽은 [화면별 `accessory`] [설정 `gearshape`] [알림 `bell`] 순으로 고정이다(contract "알림 아이콘은 다섯 탭 모두 우상단 고정",
/// "설정 — 진입"). 설정은 `SettingsRoute`를 미는 `NavigationLink`라서 탭의 `NavigationStack`에 `.navigationDestination(for: SettingsRoute.self)`가 있어야 한다(`HomePage`가 단다).
/// 알림 화면은 아직 자리만이라 기존 안내 문구를 띄운다. 뒤로 가기가 있는 하위 화면은 inline 내비게이션 제목을 유지한다.
struct PageTitle<Accessory: View>: View {
    let title: String
    let accessory: Accessory
    @State private var showNotice = false
    init(_ title: String, @ViewBuilder accessory: () -> Accessory) {
        self.title = title
        self.accessory = accessory()
    }
    var body: some View {
        HStack(alignment: .center, spacing: 4) {
            Text(title).font(.dearby(.title).bold()).accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            accessory.padding(.trailing, 8)
            NavigationLink(value: SettingsRoute()) {
                Image(systemName: "gearshape").imageScale(.large).frame(width: 44, height: 44)
            }.accessibilityLabel("설정").accessibilityIdentifier("page-settings")
            Button { showNotice = true } label: { Image(systemName: "bell").imageScale(.large).frame(width: 44, height: 44) }
                .accessibilityLabel("알림").accessibilityIdentifier("page-notifications")
        }
        .tint(DearbyStyle.teal)
        .padding(.leading, 20).padding(.trailing, 12).padding(.top, 8)
        .alert("알림", isPresented: $showNotice) { Button("확인") {} } message: { Text("새로운 알림이 없어요.") }
    }
}

extension PageTitle where Accessory == EmptyView {
    init(_ title: String) { self.init(title) { EmptyView() } }
}

/// 제목 줄의 설정 버튼이 미는 값. 설정 화면 자체는 `widgets`에 있어 `HomePage`가 목적지를 단다.
struct SettingsRoute: Hashable {}

#if DEBUG
#Preview("탭 제목") {
    NavigationStack {
        VStack(alignment: .leading, spacing: 12) {
            PageTitle("내 명함")
            PageTitle("내 활동") { DearbyBadge(title: "예시") }
            Spacer()
        }.background(Color(.systemGroupedBackground))
    }
}
#endif
