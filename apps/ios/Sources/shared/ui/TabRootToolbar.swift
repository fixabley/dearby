import SwiftUI

/// 네 탭 루트의 우상단 액세서리(contract "iOS 상단 바는 시스템 내비게이션 바" 2026-10-20): 시스템 내비게이션 바의 `topBarTrailing`에 [설정 `gearshape`] [알림 `bell`]을
/// 한 묶음으로 둔다(iOS 26에서는 시스템이 글래스 캡슐로 묶어 그린다). 화면별 액세서리(프로필의 변경 이력·명함 만들기, 내 활동의 예시 배지)는 같은 `.toolbar`에서
/// 이 묶음보다 먼저 선언해 왼쪽에 온다. 설정은 `SettingsRoute`를 미는 `NavigationLink`라서 탭의 `NavigationStack`에 `.navigationDestination(for: SettingsRoute.self)`가 있어야 한다(`HomePage`가 단다).
struct TabRootToolbar: ToolbarContent {
    var body: some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            NavigationLink(value: SettingsRoute()) { Image(systemName: "gearshape") }
                .accessibilityLabel("설정").accessibilityIdentifier("page-settings")
            NotificationsButton()
        }
    }
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
