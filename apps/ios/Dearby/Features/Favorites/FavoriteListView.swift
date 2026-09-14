import SwiftUI

struct FavoriteListView: View {
    let catalog: ActivityCatalog
    let favoriteIDs: Set<String>
    let removeOrganization: (String) -> Void

    private var savedOrganizations: [ActivityOrganization] {
        catalog.organizations.filter { favoriteIDs.contains($0.id) }
    }

    var body: some View {
        Group {
            if savedOrganizations.isEmpty {
                ContentUnavailableView("저장한 조직이 없어요", systemImage: "heart",
                                       description: Text("발견 탭의 공고를 더블탭하면 조직이 여기에 저장돼요."))
            } else {
                List(savedOrganizations) { organization in
                    FavoriteOrganizationRow(
                        organization: organization,
                        catalog: catalog,
                        remove: { removeOrganization(organization.id) }
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
