import SwiftUI

struct CalendarTimelineView: View {
    let item: CalendarOverlapState
    private let height: CGFloat = 260
    private let axis: CGFloat = 40
    private let orange = Color(red: 0.92, green: 0.48, blue: 0.08)
    var body: some View {
        VStack(spacing: 10) {
            HStack {
                Color.clear.frame(width: axis)
                Text("이 활동").frame(maxWidth: .infinity)
                Text("연결한 캘린더").frame(maxWidth: .infinity)
            }.font(.subheadline.bold())
            GeometryReader { geometry in
                let width = geometry.size.width - axis
                let column = width / 2
                ZStack(alignment: .topLeading) {
                    ForEach(0...item.gridSteps, id: \.self) { step in
                        let date = item.gridStart.addingTimeInterval(Double(step) * 1800)
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
                    block(title: item.activity.title, start: item.activity.start, end: item.activity.end,
                          color: Color(red: 0.69, green: 0.87, blue: 0.86), width: column - 16)
                        .offset(x: axis + 12, y: y(max(item.activity.start, item.gridStart)))
                    block(title: "예시 바쁜 시간", start: item.busy.start, end: item.busy.end,
                          color: Color(red: 0.71, green: 0.83, blue: 0.94), width: column - 12)
                        .offset(x: axis + column + 6, y: y(max(item.busy.start, item.gridStart)))
                    Rectangle().fill(orange.opacity(0.14))
                        .frame(width: width, height: y(item.end) - y(item.start))
                        .offset(x: axis, y: y(item.start))
                    ForEach([item.start, item.end], id: \.self) { date in
                        Path { path in
                            path.move(to: CGPoint(x: axis, y: y(date)))
                            path.addLine(to: CGPoint(x: geometry.size.width, y: y(date)))
                        }.stroke(orange, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
                    }
                    Text("겹치는 구간").font(.caption2.bold()).foregroundStyle(Color(red: 0.58, green: 0.27, blue: 0))
                        .padding(.horizontal, 5).padding(.vertical, 3).background(.white.opacity(0.88), in: Capsule())
                        .position(x: axis + column, y: (y(item.start) + y(item.end)) / 2)
                }
            }.frame(height: height)
            Text("Asia/Seoul · 30분 간격").font(.caption2).foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("이 활동 \(item.activity.title), \(item.time(item.activity.start))부터 \(item.time(item.activity.end)). 예시 바쁜 시간 \(item.time(item.busy.start))부터 \(item.time(item.busy.end)). 겹치는 시간 \(item.minutes)분. 시간대 \(item.activity.timeZone).")
    }
    private func y(_ date: Date) -> CGFloat {
        CGFloat(date.timeIntervalSince(item.gridStart) / item.gridEnd.timeIntervalSince(item.gridStart)) * height
    }
    private func block(title: String, start: Date, end: Date, color: Color, width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if start < item.gridStart { Text("이전부터 계속 ⋮").font(.system(size: 9)) }
            Text(title).font(.system(size: 11, weight: .semibold)).fixedSize(horizontal: false, vertical: true)
            Text("\(item.time(start)) – \(item.time(end))").font(.system(size: 10)).foregroundStyle(.secondary)
            Spacer(minLength: 0)
            if end > item.gridEnd {
                Text("이후에도 계속\n⋮").font(.system(size: 10)).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
        }.padding(8).frame(width: width, height: y(min(end, item.gridEnd)) - y(max(start, item.gridStart)), alignment: .topLeading)
            .background(color, in: RoundedRectangle(cornerRadius: 8))
    }
}
