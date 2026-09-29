import SwiftUI

struct CalendarOverlapTimeline: View {
    let item: CalendarOverlapState
    private let height: CGFloat = 300
    private let gutter: CGFloat = 46
    private var duration: TimeInterval { item.timelineEnd.timeIntervalSince(item.timelineStart) }
    private func position(_ date: Date) -> CGFloat {
        CGFloat(min(max(date.timeIntervalSince(item.timelineStart) / duration, 0), 1)) * height
    }
    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                Text("이 활동").frame(maxWidth: .infinity)
                Text("연결한 캘린더").frame(maxWidth: .infinity)
            }.font(.subheadline.bold()).padding(.leading, gutter)
            GeometryReader { geometry in
                let width = (geometry.size.width - gutter - 20) / 2
                ZStack(alignment: .topLeading) {
                    grid(width: geometry.size.width)
                    block(title: item.activity.title, start: item.activity.start, end: item.activity.end,
                          color: DearbyStyle.teal, width: width)
                        .offset(x: gutter + 4, y: position(item.activity.start))
                    block(title: "바쁜 시간", start: item.busy.start, end: item.busy.end,
                          color: .blue, width: width)
                        .offset(x: gutter + width + 16, y: position(item.busy.start))
                    Rectangle().fill(.orange.opacity(0.14))
                        .frame(width: geometry.size.width - gutter, height: max(1, position(item.end) - position(item.start)))
                        .offset(x: gutter, y: position(item.start))
                    ForEach([item.start, item.end], id: \.self) { date in
                        Path { path in
                            path.move(to: CGPoint(x: gutter, y: position(date)))
                            path.addLine(to: CGPoint(x: geometry.size.width, y: position(date)))
                        }.stroke(.orange, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
                    }
                }
            }.frame(height: height + 10)
            Label("주황색 띠는 겹치는 구간이에요", systemImage: "rectangle.fill")
                .font(.caption).foregroundStyle(.orange)
        }
        .padding(.top, 8)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("이 활동, \(item.activity.title), \(item.timeRange(item.activity.start, item.activity.end)). 연결한 캘린더, 바쁜 시간, \(item.timeRange(item.busy.start, item.busy.end)). 겹치는 구간, \(item.timeRange(item.start, item.end)).")
    }
    private func grid(width: CGFloat) -> some View {
        ForEach(0...4, id: \.self) { index in
            let date = item.timelineStart.addingTimeInterval(duration * Double(index) / 4)
            Text(item.timeText(date, includeDate: duration >= 86400 || item.timeText(date).hasPrefix("00:")))
                .font(.caption2).foregroundStyle(.secondary).frame(width: gutter - 4, alignment: .leading)
                .offset(y: position(date) - 7)
            Path { path in
                path.move(to: CGPoint(x: gutter, y: position(date)))
                path.addLine(to: CGPoint(x: width, y: position(date)))
            }.stroke(DearbyStyle.line, lineWidth: 1)
        }
    }
    private func block(title: String, start: Date, end: Date, color: Color, width: CGFloat) -> some View {
        let blockHeight = max(1, position(end) - position(start))
        return RoundedRectangle(cornerRadius: 8).fill(color.opacity(0.22))
            .overlay(alignment: .topLeading) {
                if blockHeight >= 54 {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(title).font(.caption.bold()).lineLimit(2)
                        Text(item.timeRange(start, end)).font(.caption2)
                        if start < item.timelineStart { Text("이전부터 계속").font(.caption2) }
                    }.padding(8)
                }
            }
            .overlay(alignment: .bottom) {
                if end > item.timelineEnd && blockHeight >= 110 {
                    Label("이후에도 계속", systemImage: "ellipsis").font(.caption2).padding(8)
                }
            }
            .frame(width: width, height: blockHeight).clipped()
    }
}
