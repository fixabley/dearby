import SwiftUI

/// App owns the OS action and presents failure on the active sheet/navigation destination.
struct NoticeDetailDestination: View {
    let notice: ActivityNotice
    let catalog: ActivityCatalog
    @Environment(\.openURL) private var openURL
    @State private var mapFailed = false

    var body: some View {
        NoticeDetailView(notice: notice, catalog: catalog, onOpenMap: openMap)
            .alert("지도를 열지 못했어요", isPresented: $mapFailed) {
                Button("확인", role: .cancel) {}
            } message: {
                Text("잠시 후 다시 시도해 주세요.")
            }
    }

    private func openMap(_ venue: ActivityVenue) {
        VenueMapLauncher.open(venue, using: { url, completion in
            openURL(url, completion: completion)
        }, onFailure: { mapFailed = true })
    }
}
