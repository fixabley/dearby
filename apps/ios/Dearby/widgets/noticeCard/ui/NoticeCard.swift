import SwiftUI

/// Connected widget: reads its own VM, delegates save, and reports feedback to the page.
struct NoticeCard: View {
    let viewModel: NoticeCardViewModel
    let position: String
    let compact: Bool
    let onSaved: (SaveOrganizationResult) -> Void
    let onShowDetail: () -> Void
    var scrollSchedules = true

    var body: some View {
        if let state = viewModel.state {
            VenueMapPresentation(failureMessage: "지도를 열지 못했어요.") { openMap in
                NoticeCardContent(state: state, position: position, compact: compact,
                onSave: { onSaved(viewModel.save()) }, onShowDetail: onShowDetail,
                scrollSchedules: scrollSchedules, onOpenMap: { schedule, venue in
                    if let location = viewModel.venue(scheduleIndex: schedule, venueIndex: venue) { openMap(location) }
                })
            }
        }
    }
}
