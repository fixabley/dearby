import SwiftUI

struct DiscoveryRootView: View {
    let state: DiscoveryState
    var body: some View {
        NavigationStack { CatalogPage(state: state.catalog, saved: false, showsSaving: false) }
            .task { await state.catalog.refresh() }
    }
}
