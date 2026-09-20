import SwiftUI

/// The details action carries a stable notice identity while App chooses the destination.
struct NoticeDetailsButton: View {
    let noticeID: String
    let onShowDetail: () -> Void

    var body: some View {
        SecondaryButton(action: onShowDetail) {
            Label("공고 정보 · 출처 보기", systemImage: "info.circle").labelStyle(.iconOnly)
                .frame(maxWidth: .infinity)
        }
        .accessibilityIdentifier("details.\(noticeID)")
    }
}
