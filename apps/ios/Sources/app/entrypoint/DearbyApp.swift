import SwiftUI

@main struct DearbyApp: App {
    @State private var catalog: CatalogViewModel
    @State private var identity = IdentityViewModel()
    @State private var share: QRShareModel
    @State private var tab = 0
    // A universal link opened the app; the QR tab turns it into the shared-card screen.
    @State private var opened: URL?
    private let links: AppLinkProvider
    init() {
        let links = AppLinkProvider()
        self.links = links
        _catalog = State(initialValue: CatalogViewModel(fetch: links.catalog))
        _share = State(initialValue: QRShareModel(account: links.account(), link: links.shareURL))
    }
    var body: some Scene {
        WindowGroup {
            HomePage(selectedTab: $tab,
                discovery: { CatalogPage(state: catalog, path: $0) }, mine: MyActivitiesPage(state: catalog, explore: { tab = 0 }),
                qr: QRPage(state: identity, share: share,
                    activities: catalog.appliedActivities, parse: links.scanned, opened: $opened), wallet: WalletPage(state: identity), profile: ProfilePage(state: identity))
            .onOpenURL { url in
                guard links.shareID(url) != nil else { return }
                tab = 2
                opened = url
            }
        }
    }
}
