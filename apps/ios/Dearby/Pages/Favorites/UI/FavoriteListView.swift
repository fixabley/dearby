import SwiftUI

struct FavoriteListView<Destination: View>: View {
    let cards: [FavoriteOrganizationCardState]
    let removeOrganization: (String) -> Void
    @ViewBuilder let destination: (String) -> Destination

    var body: some View {
        Group {
            if cards.isEmpty {
                ContentUnavailableView("저장한 조직이 없어요", systemImage: "heart",
                                       description: Text("발견 탭의 공고를 더블탭하면 조직이 여기에 저장돼요."))
            } else {
                List(cards) { organization in
                    FavoriteOrganizationCard(
                        state: organization,
                        remove: { removeOrganization(organization.id) },
                        destination: destination
                    )
                }
                .listStyle(.insetGrouped)
            }
        }
        .navigationTitle("즐겨찾기")
        .safeAreaInset(edge: .bottom) {
            Text("이 기기에 저장돼요").font(.caption).foregroundStyle(.secondary).padding(8)
        }
    }
}
