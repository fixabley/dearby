import SwiftUI

/// One source-zone day; visual minimum height never changes the exact accessibility interval.
struct EventTimelineGrid: View {
    let day: EventTimelineInterval.Day
    let title: String
    let hourHeight: Double
    let gutter: Double
    let busy: BusyTimeDisplay
    let timeZone: TimeZone
    var body: some View {
        let hasOverlap = busy.status == .ready && !busy.overlaps.isEmpty
        let blockHeight = max(44, day.duration * hourHeight)
        return VStack(spacing: 0) {
            ForEach(Array(day.ticks.enumerated()), id: \.element.id) { index, tick in
                let nextOffset = day.ticks.indices.contains(index + 1) ? day.ticks[index + 1].offset : day.hours + 1
                HStack(alignment: .top, spacing: NativeSpacing.compact) {
                    Text(tick.label).font(.caption2).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                        .frame(width: gutter, alignment: .trailing)
                    Rectangle().fill(.separator).frame(height: 0.5).padding(.top, 12)
                }
                .frame(height: (nextOffset - tick.offset) * hourHeight, alignment: .top)
                .id(tick.id).accessibilityHidden(true)
            }
        }
        .overlay(alignment: .topLeading) {
            GeometryReader { geometry in
                let available = max(40, geometry.size.width - gutter - 12)
                VStack(alignment: .leading, spacing: NativeSpacing.compact) {
                    HStack(alignment: .top) {
                        if hasOverlap { Image(systemName: "exclamationmark.triangle.fill").accessibilityHidden(true) }
                        Text(title).font(.caption.weight(.semibold)).lineLimit(day.duration * hourHeight < 80 ? 1 : 2)
                    }
                    .frame(maxWidth: hasOverlap ? available * 0.50 : .infinity, alignment: .leading)
                    Spacer(minLength: 0)
                }
                .padding(NativeSpacing.related)
                .frame(width: available, height: blockHeight, alignment: .topLeading)
                .clipped()
                .background(Color.accentColor.opacity(0.22), in: RoundedRectangle(cornerRadius: 6))
                .overlay(alignment: .leading) { RoundedRectangle(cornerRadius: 2).fill(Color.accentColor).frame(width: 3) }
                .offset(x: gutter + 8, y: day.offset * hourHeight + 12)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(title), \(day.summary)\(hasOverlap ? ", 활동과 겹치는 기기 일정 있음" : "")")
            }
        }
        .background(alignment: .topLeading) {
            if busy.status == .ready && !busy.intervals.isEmpty { BusyTimeBlocks(display: busy, day: day, hourHeight: hourHeight, gutter: gutter, timeZone: timeZone) }
        }
        .overlay(alignment: .topLeading) {
            if busy.status == .ready {
                BusyTimeOverlay(display: busy, day: day, hourHeight: hourHeight, gutter: gutter, timeZone: timeZone)
            }
        }
        .padding(.horizontal, NativeSpacing.compact)
    }
}
