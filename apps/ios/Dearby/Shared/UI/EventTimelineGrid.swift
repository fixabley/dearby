import SwiftUI

/// One source-zone day; visual minimum height never changes the exact accessibility interval.
struct EventTimelineGrid: View {
    let day: EventTimelineInterval.Day
    let title: String
    let hourHeight: Double
    let gutter: Double
    var body: some View {
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
                VStack(alignment: .leading, spacing: NativeSpacing.compact) {
                    Text(title).font(.caption.weight(.semibold)).lineLimit(day.duration * hourHeight < 80 ? 1 : 2)
                    Spacer(minLength: 0)
                }
                .padding(NativeSpacing.related)
                .frame(width: max(40, geometry.size.width - gutter - 12), height: blockHeight, alignment: .topLeading)
                .clipped()
                .background(Color.accentColor.opacity(0.22), in: RoundedRectangle(cornerRadius: 6))
                .overlay(alignment: .leading) { RoundedRectangle(cornerRadius: 2).fill(Color.accentColor).frame(width: 3) }
                .offset(x: gutter + 8, y: day.offset * hourHeight + 12)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(title), \(day.summary)")
            }
        }
        .padding(.horizontal, NativeSpacing.compact)
    }
}
