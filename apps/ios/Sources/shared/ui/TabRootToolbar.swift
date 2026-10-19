import SwiftUI

/// 네 탭 루트의 우상단 액세서리(contract "iOS 상단 바는 시스템 내비게이션 바" 2026-10-20): 시스템 내비게이션 바의 `topBarTrailing`에 [설정 `gearshape`] [알림 `bell`]을
/// 한 묶음으로 둔다(iOS 26에서는 시스템이 글래스 캡슐로 묶어 그린다). 화면별 액세서리(프로필의 변경 이력·명함 만들기, 내 활동의 예시 배지)는 같은 `.toolbar`에서
/// 이 묶음보다 먼저 선언해 왼쪽에 온다. 설정은 `SettingsRoute`를 미는 `NavigationLink`라서 탭의 `NavigationStack`에 `.navigationDestination(for: SettingsRoute.self)`가 있어야 한다(`HomePage`가 단다).
/// 메뉴(`line.3.horizontal`)의 한 항목: 아이콘 + 라벨로 보이는 보조 행동.
struct DearbyMenuItem: Identifiable {
    let title: String
    let systemImage: String
    let action: () -> Void
    var id: String { title }
    init(_ title: String, systemImage: String, action: @escaping () -> Void) {
        self.title = title
        self.systemImage = systemImage
        self.action = action
    }
}

/// 네 탭 루트의 우상단(contract "상단 바 구성(네 탭 공통)" 2026-10-20): [탭 고유 주 행동 `primary`] [설정] [알림] [메뉴 `line.3.horizontal`, 접근성 `더 보기`] 순.
/// 메뉴는 `menu`가 비면 숨고, 항목은 `Label(제목, systemImage:)` 버튼이다. 설정·알림·주 행동은 메뉴에 넣지 않는다.
struct TabRootToolbar<Primary: View>: ToolbarContent {
    var menu: [DearbyMenuItem] = []
    @ViewBuilder let primary: Primary
    init(menu: [DearbyMenuItem] = [], @ViewBuilder primary: () -> Primary) {
        self.menu = menu
        self.primary = primary()
    }
    var body: some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            primary
            NavigationLink(value: SettingsRoute()) { Image(systemName: "gearshape") }
                .accessibilityLabel("설정").accessibilityIdentifier("page-settings")
            NotificationsButton()
            if !menu.isEmpty {
                Menu {
                    ForEach(menu) { item in Button(action: item.action) { Label(item.title, systemImage: item.systemImage) } }
                } label: {
                    Image(systemName: "line.3.horizontal")
                }.accessibilityLabel("더 보기").accessibilityIdentifier("page-more")
            }
        }
    }
}

extension TabRootToolbar where Primary == EmptyView {
    init(menu: [DearbyMenuItem] = []) { self.init(menu: menu) { EmptyView() } }
}

/// 알림 버튼: 알림 화면은 아직 자리만이라 기존 안내 문구를 띄운다(contract "알림 아이콘은 … 우상단 고정").
private struct NotificationsButton: View {
    @State private var showNotice = false
    var body: some View {
        Button { showNotice = true } label: { Image(systemName: "bell") }
            .accessibilityLabel("알림").accessibilityIdentifier("page-notifications")
            .alert("알림", isPresented: $showNotice) { Button("확인") {} } message: { Text("새로운 알림이 없어요.") }
    }
}

/// 설정 버튼이 미는 값. 설정 화면 자체는 `widgets`에 있어 `HomePage`가 목적지를 단다.
struct SettingsRoute: Hashable {}
