import SwiftUI

struct NoticeScheduleView: View {
    let state: NoticeScheduleState
    let onAddToCalendar: (() -> Void)?
    let onOpenMap: (NoticeVenue) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: NativeSpacing.related) {
            Text(state.title).font(.headline).accessibilityAddTraits(.isHeader)
            MetadataRow(systemImage: "calendar", text: state.period,
                        accessibilityText: "\(state.title), \(state.period)")
            MetadataRow(systemImage: "mappin.and.ellipse", text: state.location)
            HStack {
                if let onAddToCalendar {
                    CalendarAddButton(onAdd: onAddToCalendar)
                        .accessibilityLabel("\(state.title) 캘린더에 추가: \(state.period)")
                }
                ForEach(Array(state.venues.enumerated()), id: \.offset) { _, venue in
                    VenueMapButton(venue: venue, onOpenMap: onOpenMap)
                }
            }
        }
    }
}
