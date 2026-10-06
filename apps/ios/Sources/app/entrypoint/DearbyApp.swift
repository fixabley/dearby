import SwiftUI

@main struct DearbyApp: App {
    @State private var catalog = CatalogViewModel()
    @State private var identity = IdentityViewModel()
    @State private var tab = 0
    var body: some Scene {
        WindowGroup {
            HomePage(selectedTab: $tab,
                discovery: CatalogPage(state: catalog), mine: MyActivitiesPage(state: catalog, explore: { tab = 0 }),
                qr: QRPage(state: identity), wallet: WalletPage(state: identity), profile: ProfilePage(state: identity))
        }
    }
}
