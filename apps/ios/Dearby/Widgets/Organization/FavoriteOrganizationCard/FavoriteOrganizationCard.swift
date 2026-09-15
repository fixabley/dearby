import SwiftUI

struct FavoriteOrganizationCard<Destination: View>: View {
    let state: FavoriteOrganizationCardState
    let remove: () -> Void
    @ViewBuilder let destination: (String) -> Destination

    var body: some View {
        Section {
            if !state.ancestorNames.isEmpty {
                InformationRow(title: "상위 조직", value: state.ancestorNames, systemImage: "building.2")
            }
            if state.notices.isEmpty {
                StatusMessage(text: "현재 연결된 공고가 없어요", systemImage: "doc.text")
            }
            ForEach(state.notices) { notice in
                NavigationLink {
                    destination(notice.id)
                } label: {
                    VStack(alignment: .leading, spacing: NativeSpacing.compact) {
                        Text(notice.title).font(.body)
                        NoticeClassificationView(category: notice.category,
                                                 contextNames: notice.contextNames)
                    }
                    .frame(minWidth: 44, minHeight: 44, alignment: .leading)
                }
            }
            Button(role: .destructive, action: remove) {
                Label("즐겨찾기에서 삭제", systemImage: "heart.slash").labelStyle(.iconOnly)
                    .frame(minWidth: 44, minHeight: 44, alignment: .leading)
            }
            .accessibilityLabel("\(state.name) 즐겨찾기에서 삭제")
            .accessibilityIdentifier("remove.\(state.id)")
        } header: {
            Text(state.name).font(.headline)
                .foregroundStyle(.primary)
                .textCase(nil)
        } footer: {
            if !state.notices.isEmpty {
                Text("연결된 공고 \(state.notices.count)개")
            }
        }
    }
}

#Preview("조직 · 큰 글자") {
    NavigationStack {
        List {
            FavoriteOrganizationCard(
                state: .init(id: "preview", name: "긴 이름의 관심 조직", ancestorNames: "대학교 › 단과대학",
                             notices: [.init(id: "notice", title: "누구나 참여할 수 있는 활동 안내", category: "교육", contextNames: "대학교")]),
                remove: {}, destination: { Text($0) })
        }
    }
    .environment(\.dynamicTypeSize, .accessibility3)
}
