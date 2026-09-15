import SwiftUI

/// A fixed feed page; large text scrolls inside this card without changing outer page extent.
struct DiscoveryCardPage: View {
    let viewModel: NoticeCardViewModel
    let position: String
    let viewport: CGSize
    let scrollContents: Bool
    let onSaved: (SaveOrganizationResult) -> Void
    let onShowDetail: () -> Void
    var onOpenMap: (Int, Int) -> Void = { _, _ in }
    var body: some View {
        Group {
            if scrollContents {
                ScrollView(.vertical) {
                    NoticeCard(viewModel: viewModel, position: position, compact: viewport.height < 520,
                        onSaved: onSaved, onShowDetail: onShowDetail, scrollSchedules: false, onOpenMap: onOpenMap)
                        .frame(minHeight: viewport.height)
                }
                .scrollBounceBehavior(.basedOnSize)
                .accessibilityLabel("공고 내용 · \(position)")
            } else {
                NoticeCard(viewModel: viewModel, position: position, compact: viewport.height < 520,
                    onSaved: onSaved, onShowDetail: onShowDetail, onOpenMap: onOpenMap)
            }
        }
        .frame(width: viewport.width, height: viewport.height)
        .clipped()
    }
}
