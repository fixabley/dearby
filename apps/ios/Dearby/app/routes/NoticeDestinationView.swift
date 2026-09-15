import SwiftUI

/// One route is used by Discovery sheets and Favorites navigation.
struct NoticeDestinationView: View {
    let id: String
    let session: AppSession

    var body: some View {
        if let destination = session.detailPage(id) {
            destination
        } else {
            ContentUnavailableView("공고를 불러오지 못했어요", systemImage: "exclamationmark.triangle")
        }
    }
}
