import SwiftUI

/// `추천` 묶음의 `더 보기`: 서버가 준 추천 전부(이유 칩과 함께), 누르면 활동 상세.
struct RecommendationListPage: View {
    let items: [RecommendedActivity]
    let open: (RecommendedActivity) -> Void
    var body: some View {
        List(items) { item in
            Button { open(item) } label: { RecommendationRow(item: item).contentShape(Rectangle()) }.buttonStyle(.plain)
        }
        .navigationTitle("추천").navigationBarTitleDisplayMode(.inline)
    }
}

/// Pushes `RecommendationListPage` on the 발견 stack.
struct RecommendationListRoute: Hashable {}
