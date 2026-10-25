import SwiftUI

/// 명함 탭(contract "명함 탭 통합" 2026-10-20): 내 명함과 받은 명함을 한 탭의 두 구역으로. 전환기는 시스템 내비게이션 바의 제목 자리(`ToolbarItem(.principal)`)에 놓인
/// 세그먼트 `Picker`(contract "iOS 상단 바는 시스템 내비게이션 바"), 기본 `내 명함`, 선택은 앱 실행 중 메모리에만(`DearbyApp`이 들고 있어 딥링크가 받은 명함 구역을 열 수 있다).
/// 구역 내용은 기존 `MyCardsPage`·`WalletPage` 그대로.
struct CardsPage: View {
    @Binding var segment: CardsSegment
    let cards: MyCardsModel
    let wallet: WalletModel
    var body: some View {
        Group {
            switch segment {
            case .myCards: MyCardsPage(state: cards).analyticsScreen("my_cards")
            case .received: WalletPage(state: wallet).analyticsScreen("received_cards")
            }
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .navigationTitle("명함").navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Picker("명함 구역", selection: $segment.reporting { AppAnalytics.event("menu_open", ["menu": $0 == .received ? "received_cards" : "my_cards"]) }) {
                    ForEach(CardsSegment.allCases, id: \.self) { Text($0.title).tag($0) }
                }.pickerStyle(.segmented).accessibilityIdentifier("cards-segment")
            }
            TabRootToolbar()
        }
    }
}

/// The two sections of the 명함 tab.
enum CardsSegment: CaseIterable {
    case myCards, received
    var title: String {
        switch self {
        case .myCards: "내 명함"
        case .received: "받은 명함"
        }
    }
}
