import SwiftUI

struct FavoriteOrganizationCard<Destination: View>: View {
    let state: FavoriteOrganizationCardState
    let remove: () -> Void
    @ViewBuilder let destination: (String) -> Destination

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(state.name).font(.headline)
            if !state.ancestorNames.isEmpty {
                Text(state.ancestorNames).font(.caption).foregroundStyle(.secondary)
            }
            let notices = state.notices
            Text(notices.isEmpty ? "현재 연결된 공고가 없어요" : "연결된 공고 \(notices.count)개")
                .font(.caption).foregroundStyle(.secondary)
            ForEach(notices) { notice in
                NavigationLink {
                    destination(notice.id)
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(notice.title)
                        NoticeClassificationView(category: notice.category,
                                                 contextNames: notice.contextNames)
                    }
                }
            }
            Button("즐겨찾기에서 삭제", role: .destructive, action: remove)
                .font(.caption)
                .accessibilityIdentifier("remove.\(state.id)")
        }.padding(.vertical, 6)
    }
}
