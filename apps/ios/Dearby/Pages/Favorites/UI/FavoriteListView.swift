import SwiftUI

struct FavoriteListView<Destination: View>: View {
    let catalog: NoticeCatalog
    let favoriteIDs: Set<String>
    let removeOrganization: (String) -> Void
    @ViewBuilder let destination: (Notice) -> Destination

    private var savedOrganizations: [NoticeOrganization] {
        catalog.organizations.filter { favoriteIDs.contains($0.id) }
    }

    var body: some View {
        Group {
            if savedOrganizations.isEmpty {
                ContentUnavailableView("저장한 조직이 없어요", systemImage: "heart",
                                       description: Text("발견 탭의 공고를 더블탭하면 조직이 여기에 저장돼요."))
            } else {
                List(savedOrganizations) { organization in
                    FavoriteOrganizationCard(
                        organization: organization,
                        catalog: catalog,
                        remove: { removeOrganization(organization.id) },
                        destination: destination
                    )
                }
            }
        }
        .navigationTitle("즐겨찾기")
        .safeAreaInset(edge: .bottom) {
            Text("이 기기에 저장돼요").font(.caption).foregroundStyle(.secondary).padding(8)
        }
    }
}
