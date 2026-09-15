import SwiftUI

struct FavoriteOrganizationCard<Destination: View>: View {
    let viewModel: FavoriteOrganizationCardViewModel
    @ViewBuilder let destination: (String) -> Destination
    var body: some View {
        if let state = viewModel.state {
            FavoriteOrganizationCardContent(state: state, remove: viewModel.remove, destination: destination)
        }
    }
}
