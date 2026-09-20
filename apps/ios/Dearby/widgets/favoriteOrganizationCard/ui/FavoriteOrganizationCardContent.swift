import SwiftUI

struct FavoriteOrganizationCardContent<Destination: View>: View {
    let state: FavoriteOrganizationCardState
    let remove: () -> Void
    @ViewBuilder let destination: (String) -> Destination

    var body: some View {
        OrganizationSummary(name: state.name, ancestors: state.ancestorNames, noticeCount: state.notices.count) {
            ForEach(state.notices) { notice in
                NavigationLink {
                    destination(notice.id)
                } label: {
                    NoticePreviewLabel(title: notice.title, category: notice.category, contextNames: notice.contextNames)
                }
            }
            Button(role: .destructive, action: remove) {
                Label("즐겨찾기에서 삭제", systemImage: "heart.slash").labelStyle(.iconOnly)
                    .frame(minWidth: 44, minHeight: 44, alignment: .leading)
            }
            .accessibilityLabel("\(state.name) 즐겨찾기에서 삭제")
            .accessibilityIdentifier("remove.\(state.id)")
        }
    }
}

#Preview("조직 · 큰 글자") {
    NavigationStack {
        List {
            FavoriteOrganizationCardContent(
                state: .init(id: "preview", name: "긴 이름의 관심 조직", ancestorNames: "대학교 › 단과대학",
                             notices: [.init(id: "notice", title: "누구나 참여할 수 있는 활동 안내", category: "교육", contextNames: "대학교")]),
                remove: {}, destination: { Text($0) })
        }
    }
    .environment(\.dynamicTypeSize, .accessibility3)
}
