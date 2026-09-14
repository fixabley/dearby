import SwiftUI

struct NoticeScheduleView: View {
    let state: NoticeScheduleState
    let onAddToCalendar: (() -> Void)?
    let onOpenMap: (NoticeVenue) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: NativeSpacing.content) {
            HStack(alignment: .top) {
                Text(state.title).font(.headline).accessibilityAddTraits(.isHeader)
                Spacer()
                if let onAddToCalendar {
                    CalendarAddButton(onAdd: onAddToCalendar)
                        .accessibilityLabel("\(state.title) 캘린더에 추가: \(state.period)")
                }
            }
            EventTimeRows(lines: state.time.lines, note: state.time.note)
            if let interval = state.time.timeline {
                EventDayTimeline(interval: interval, title: state.title)
            } else {
                Text("시작·종료 시각이 확인되면 시간표를 표시합니다.").font(.footnote).foregroundStyle(.secondary)
            }
            ForEach(Array(state.places.enumerated()), id: \.offset) { _, place in
                LocationInformation(name: place.display.name, detail: place.display.detail) {
                    if place.venue.coordinates != nil { VenueMapButton(venue: place.venue, onOpenMap: onOpenMap) }
                }
            }
            if state.places.isEmpty {
                LocationInformation(name: state.location, detail: nil) { EmptyView() }
            }
            if let url = state.onlineURL {
                ExternalLinkCard(url: url, label: "온라인 장소")
            }
        }
    }
}
