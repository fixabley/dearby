import SwiftUI

/// Anonymous busy intervals behind the activity, using exact elapsed-time geometry.
struct BusyTimeBlocks: View {
    let display: BusyTimeDisplay
    let day: EventTimelineInterval.Day
    let hourHeight: Double
    let gutter: Double
    let timeZone: TimeZone
    var body: some View {
        GeometryReader { geometry in
            ForEach(Array(display.intervals.enumerated()), id: \.offset) { _, interval in
                if let clipped = interval.clipped(to: DateInterval(start: day.start, end: day.end)) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(.secondary.opacity(0.20))
                        .frame(width: max(40, geometry.size.width - gutter - 12), height: clipped.end.timeIntervalSince(clipped.start) / 3600 * hourHeight)
                        .offset(x: gutter + 8, y: clipped.start.timeIntervalSince(day.start) / 3600 * hourHeight + 12)
                        .accessibilityHidden(true)
                }
            }
        }
    }
}
