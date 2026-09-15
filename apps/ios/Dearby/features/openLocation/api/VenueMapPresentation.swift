import SwiftUI

struct VenueMapPresentation<Content: View>: View {
    @Environment(\.openURL) private var openURL
    @State private var failed = false
    var failureMessage = "잠시 후 다시 시도해 주세요."
    @ViewBuilder let content: (@escaping (NoticeVenue) -> Void) -> Content

    var body: some View {
        content(open)
            .alert("지도를 열지 못했어요", isPresented: $failed) {
                Button("확인", role: .cancel) {}
            } message: { Text(failureMessage) }
    }

    private func open(_ venue: NoticeVenue) {
        VenueMapLauncher.open(venue, using: { url, completion in openURL(url, completion: completion) },
            onFailure: { failed = true })
    }
}
