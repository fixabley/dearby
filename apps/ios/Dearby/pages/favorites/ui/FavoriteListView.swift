import SwiftUI

struct FavoriteListView<Destination: View>: View {
    let viewModels: [FavoriteOrganizationCardViewModel]
    private var cards: [FavoriteOrganizationCardViewModel] { viewModels.filter { $0.state != nil } }
    @ViewBuilder let destination: (String) -> Destination

    var body: some View {
        SavedOrganizationList(isEmpty: cards.isEmpty) {
            List(cards) { organization in
                FavoriteOrganizationCard(viewModel: organization, destination: destination)
            }
            .listStyle(.insetGrouped)
        }
        .navigationTitle("즐겨찾기")
    }
}
