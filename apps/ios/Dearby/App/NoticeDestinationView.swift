import SwiftUI

/// App routing composition shared by Discovery sheets and Favorites navigation.
struct NoticeDestinationView: View {
    let id: String
    let session: NoticeSession

    var body: some View {
        if let state = session.detailState(id), let notice = session.notices.cachedNotice(id) {
            NoticeDetailDestination(state: state, notice: notice)
        } else {
            ContentUnavailableView("공고를 불러오지 못했어요", systemImage: "exclamationmark.triangle")
        }
    }
}
