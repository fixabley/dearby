import SwiftUI

/// Full readable times remain available even when a short block cannot fit text.
struct BusyTimeSummaryView: View {
    let display: BusyTimeDisplay
    let timeZone: TimeZone
    var body: some View {
        DisclosureGroup("바쁜 시간 \(display.intervals.count)개 · 시간 보기") {
            ForEach(Array(display.intervals.enumerated()), id: \.offset) { _, interval in
                let overlaps = display.overlaps.contains { max($0.start, interval.start) < min($0.end, interval.end) }
                Label("바쁜 시간 · \(interval.description(in: timeZone))\(overlaps ? " · 활동과 겹침" : "")",
                      systemImage: overlaps ? "exclamationmark.triangle" : "clock")
                    .font(.footnote).fixedSize(horizontal: false, vertical: true)
            }
        }.font(.footnote)
    }
}
