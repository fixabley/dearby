import SwiftUI

struct NoticeScheduleView: View {
    let state: NoticeScheduleState
    let onAddToCalendar: (() -> Void)?
    let busyCalendar: BusyCalendarSession
    let index: Int
    let onOpenMap: (NoticeVenue) -> Void

    var body: some View {
        NoticeScheduleBody(title: state.title) {
                if let onAddToCalendar {
                    CalendarAddButton(onAdd: onAddToCalendar)
                        .accessibilityLabel("\(state.title) 캘린더에 추가: \(state.period)")
                }
        } content: {
            NoticeScheduleTimeline(state: state, session: busyCalendar, index: index)
            NoticeScheduleLocations(state: state, onOpenMap: onOpenMap)
        }
    }
}
