import SwiftUI

/// Feature composition adds query status/retry to the generic day timeline.
struct CalendarOverlapTimeline: View {
    let interval: EventTimelineInterval
    let title: String
    let busy: BusyTimeDisplay
    let onSelectDay: ((EventTimelineInterval.Day) -> Void)?
    let onRetry: () -> Void

    var body: some View {
        EventDayTimeline(interval: interval, title: title, busy: busy, onSelectDay: onSelectDay) {
            BusyTimeStatusView(display: busy, timeZone: interval.timeZone, onRetry: onRetry)
        }
    }
}
