import SwiftUI

/// Connected widget: reads its own VM, delegates save, and reports feedback to the page.
struct NoticeCard: View {
    let viewModel: NoticeCardViewModel
    let position: String
    let compact: Bool
    let onSaved: (SaveOrganizationResult) -> Void
    let onShowDetail: () -> Void
    var scrollSchedules = true
    var onOpenMap: (Int, Int) -> Void = { _, _ in }

    var body: some View {
        if let state = viewModel.state {
            NoticeCardContent(state: state, position: position, compact: compact,
                onSave: { onSaved(viewModel.save()) }, onShowDetail: onShowDetail,
                scrollSchedules: scrollSchedules, onOpenMap: onOpenMap)
        }
    }
}
