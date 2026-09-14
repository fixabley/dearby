import SwiftUI

struct FavoriteOrganizationCard<Destination: View>: View {
    let organization: NoticeOrganization
    let catalog: NoticeCatalog
    let remove: () -> Void
    @ViewBuilder let destination: (Notice) -> Destination

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(organization.name).font(.headline)
            let ancestors = catalog.organizationPath(organization.id).dropLast()
            if !ancestors.isEmpty {
                Text(ancestors.map(\.name).joined(separator: " › "))
                    .font(.caption).foregroundStyle(.secondary)
            }
            let notices = catalog.feed.filter { $0.favoriteOrganizationId == organization.id }
            Text(notices.isEmpty ? "현재 연결된 공고가 없어요" : "연결된 공고 \(notices.count)개")
                .font(.caption).foregroundStyle(.secondary)
            ForEach(notices) { notice in
                NavigationLink {
                    destination(notice)
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(notice.title)
                        NoticeClassificationView(category: notice.categorySummary,
                                                 contextNames: catalog.contextNames(for: notice))
                    }
                }
            }
            Button("즐겨찾기에서 삭제", role: .destructive, action: remove)
                .font(.caption)
                .accessibilityIdentifier("remove.\(organization.id)")
        }.padding(.vertical, 6)
    }
}
