import SwiftUI

struct CalendarTimelineView: View {
    let item: CalendarOverlapState
    /// One scale for ticks and blocks: about 0.8pt a minute, the whole chart kept between 190 and 280pt.
    private var height: CGFloat { min(max(CGFloat(item.gridEnd.timeIntervalSince(item.gridStart) / 60) * 0.8, 190), 280) }
    private let axis: CGFloat = 40
    private let orange = Color(red: 0.92, green: 0.48, blue: 0.08)
    var body: some View {
        VStack(spacing: 10) {
            HStack {
                Color.clear.frame(width: axis)
                Text("이 활동").frame(maxWidth: .infinity)
                Text("기기 캘린더").frame(maxWidth: .infinity)
            }.font(.subheadline.bold())
            GeometryReader { geometry in
                let width = geometry.size.width - axis
                let column = width / 2
                ZStack(alignment: .topLeading) {
                    ForEach(Array(stride(from: item.gridStart, through: item.gridEnd, by: item.grid.step)), id: \.self) { date in
                        Text(item.time(date)).font(.system(size: 10)).foregroundStyle(.secondary)
                            .position(x: 17, y: y(date))
                        Path { path in
                            path.move(to: CGPoint(x: axis, y: y(date)))
                            path.addLine(to: CGPoint(x: geometry.size.width, y: y(date)))
                        }.stroke(DearbyStyle.line, lineWidth: 0.6)
                    }
                    Path { path in
                        for x in [axis + 8, axis + column] {
                            path.move(to: CGPoint(x: x, y: 0))
                            path.addLine(to: CGPoint(x: x, y: height))
                        }
                    }.stroke(DearbyStyle.line, lineWidth: 0.6)
                    let activity = span(item.activity.start, item.activity.end, least: 44)
                    block(title: item.activity.title, start: item.activity.start, end: item.activity.end, height: activity.height,
                          color: Color(red: 0.69, green: 0.87, blue: 0.86), width: column - 16)
                        .offset(x: axis + 12, y: activity.top)
                    let busy = span(item.busy.start, item.busy.end, least: 44)
                    block(title: item.busyTitle, start: item.busy.start, end: item.busy.end, height: busy.height,
                          color: Color(red: 0.71, green: 0.83, blue: 0.94), width: column - 12)
                        .offset(x: axis + column + 6, y: busy.top)
                    let band = span(item.start, item.end, least: 18)
                    Rectangle().fill(orange.opacity(0.14))
                        .frame(width: width, height: band.height)
                        .offset(x: axis, y: band.top)
                    ForEach([band.top, band.top + band.height], id: \.self) { edge in
                        Path { path in
                            path.move(to: CGPoint(x: axis, y: edge))
                            path.addLine(to: CGPoint(x: geometry.size.width, y: edge))
                        }.stroke(orange, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
                    }
                    Text("겹치는 구간").font(.caption2.bold()).foregroundStyle(orange)
                        .padding(.horizontal, 5).padding(.vertical, 3).background(Color(.systemBackground).opacity(0.88), in: Capsule())
                        .position(x: axis + column, y: band.top + band.height / 2)
                }
            }.frame(height: height)
            Text("\(item.activity.timeZone) · \(stepLabel) 간격").font(.caption2).foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("이 활동 \(item.activity.title), \(item.time(item.activity.start))부터 \(item.time(item.activity.end)). 기기 일정 \(item.busyTitle) \(item.time(item.busy.start))부터 \(item.time(item.busy.end)). 겹치는 시간 \(item.minutes)분. 시간대 \(item.activity.timeZone).")
    }
    private var stepLabel: String {
        let minutes = Int(item.grid.step / 60)
        return minutes < 60 ? "\(minutes)분" : "\(minutes / 60)시간"
    }
    private func y(_ date: Date) -> CGFloat {
        CGFloat(min(max(date, item.gridStart), item.gridEnd).timeIntervalSince(item.gridStart) / item.gridEnd.timeIntervalSince(item.gridStart)) * height
    }
    /// A very short stretch still gets room for its label: grown downward, pushed up only at the chart's bottom.
    private func span(_ start: Date, _ end: Date, least: CGFloat) -> (top: CGFloat, height: CGFloat) {
        let size = min(max(y(end) - y(start), least), height)
        return (min(y(start), height - size), size)
    }
    private func block(title: String, start: Date, end: Date, height: CGFloat, color: Color, width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if start < item.gridStart { Text("이전부터 계속 ⋮").font(.system(size: 9)) }
            Text(title).font(.system(size: 11, weight: .semibold)).fixedSize(horizontal: false, vertical: true)
            Text("\(item.time(start)) – \(item.time(end))").font(.system(size: 10)).foregroundStyle(.secondary)
            Spacer(minLength: 0)
            if end > item.gridEnd {
                Text("이후에도 계속\n⋮").font(.system(size: 10)).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
        }.padding(8).frame(width: width, height: height, alignment: .topLeading)
            .background(color, in: RoundedRectangle(cornerRadius: 8))
            // The pastel fills are fixed light colours, so their labels resolve as light-mode text in dark mode too.
            .environment(\.colorScheme, .light)
    }
}
