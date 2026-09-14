import SwiftUI

/// A fixed feed page; large text scrolls inside this card without changing outer page extent.
struct DiscoveryCardPage: View {
    let state: NoticeCardState
    let position: String
    let viewport: CGSize
    let scrollContents: Bool
    let onSave: () -> Void
    let onShowDetail: () -> Void
    var body: some View {
        Group {
            if scrollContents {
                ScrollView(.vertical) {
                    NoticeCard(state: state, position: position, compact: false,
                        onSave: onSave, onShowDetail: onShowDetail)
                        .frame(minHeight: viewport.height)
                }
                .scrollBounceBehavior(.basedOnSize)
                .accessibilityLabel("공고 내용 · \(position)")
            } else {
                NoticeCard(state: state, position: position, compact: viewport.height < 520,
                    onSave: onSave, onShowDetail: onShowDetail)
            }
        }
        .frame(width: viewport.width, height: viewport.height)
        .clipped()
    }
}
