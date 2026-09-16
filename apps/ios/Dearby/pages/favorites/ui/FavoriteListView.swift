import SwiftUI

struct FavoriteListView<Destination: View>: View {
    let viewModel: FavoriteOrganizationListViewModel
    @ViewBuilder let destination: (String) -> Destination

    var body: some View {
        VStack {
            if viewModel.loadFailed {
                Text("저장한 조직을 불러오지 못했어요").font(.footnote)
                Button("다시 시도", action: viewModel.reload)
            }
            SavedOrganizationList(isEmpty: viewModel.cards.isEmpty && !viewModel.loadFailed) {
                List(viewModel.cards) { organization in
                    FavoriteOrganizationCard(viewModel: organization, destination: destination)
                }
                .listStyle(.insetGrouped)
            }
        }
        .navigationTitle("즐겨찾기")
    }
}
