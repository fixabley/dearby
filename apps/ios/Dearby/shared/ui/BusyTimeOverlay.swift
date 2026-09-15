import SwiftUI

/// Labels stay opposite activity titles; dashed outlines cover positive intersections only.
struct BusyTimeOverlay: View {
    let display: BusyTimeDisplay
    let day: EventTimelineInterval.Day
    let hourHeight: Double
    let gutter: Double
    let timeZone: TimeZone
    var body: some View {
        GeometryReader { geometry in
            let width = max(40, geometry.size.width - gutter - 12)
            ForEach(Array(display.intervals.enumerated()), id: \.offset) { _, interval in
                if let clipped = interval.clipped(to: DateInterval(start: day.start, end: day.end)) {
                    let height = clipped.end.timeIntervalSince(clipped.start) / 3600 * hourHeight
                    VStack(alignment: .trailing, spacing: NativeSpacing.compact) {
                        if height >= 44 {
                            Text("바쁜 시간").font(.caption.weight(.semibold))
                            Text(clipped.description(in: timeZone)).font(.caption2)
                        }
                    }
                    .padding(NativeSpacing.compact)
                    .frame(width: width * 0.45, height: height, alignment: .topTrailing)
                    .clipped()
                    .offset(x: gutter + 8 + width * 0.55, y: clipped.start.timeIntervalSince(day.start) / 3600 * hourHeight + 12)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("바쁜 시간, \(clipped.description(in: timeZone))")
                }
            }
            ForEach(Array(display.overlaps.enumerated()), id: \.offset) { _, interval in
                RoundedRectangle(cornerRadius: 3)
                    .strokeBorder(.primary, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                    .frame(width: width, height: interval.end.timeIntervalSince(interval.start) / 3600 * hourHeight)
                    .offset(x: gutter + 8, y: interval.start.timeIntervalSince(day.start) / 3600 * hourHeight + 12)
                    .accessibilityLabel("활동과 겹치는 시간, \(interval.description(in: timeZone))")
            }
        }
        .allowsHitTesting(false)
    }
}
