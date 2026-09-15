import SwiftUI

/// Narrow visual day preview. Native controls own date selection; no editing or export.
struct EventDayTimeline: View {
    let interval: EventTimelineInterval
    let title: String
    let busy: BusyTimeDisplay
    let onSelectDay: ((EventTimelineInterval.Day) -> Void)?
    let onRetryBusy: () -> Void
    @State private var selectedDate: Date
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .caption) private var hourHeight = 64.0
    @ScaledMetric(relativeTo: .caption) private var gutter = 62.0

    init(interval: EventTimelineInterval, title: String, busy: BusyTimeDisplay = .hidden, onSelectDay: ((EventTimelineInterval.Day) -> Void)? = nil, onRetryBusy: @escaping () -> Void = {}) {
        self.interval = interval; self.title = title
        self.busy = busy; self.onSelectDay = onSelectDay; self.onRetryBusy = onRetryBusy
        _selectedDate = State(initialValue: interval.days.lowerBound)
    }
    var body: some View {
        VStack(alignment: .leading, spacing: NativeSpacing.related) {
            Text("일간 미리보기").font(.subheadline.weight(.semibold))
            EventDaySelector(selection: $selectedDate, range: interval.days,
                timeZone: interval.timeZone, calendar: interval.calendar, title: title,
                previousDate: interval.adjacentDay(to: selectedDate, by: -1),
                nextDate: interval.adjacentDay(to: selectedDate, by: 1))
            if let day = interval.day(containing: selectedDate) {
                if dynamicTypeSize.isAccessibilitySize {
                    Text(title).font(.caption.weight(.semibold)).fixedSize(horizontal: false, vertical: true)
                }
                Text(day.summary).font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                if day.duration * hourHeight < 44 {
                    Text("짧은 일정은 블록 높이를 확대해 표시합니다.").font(.caption).foregroundStyle(.secondary)
                }
                if day.hours != 24 {
                    Text("시간대 전환일 · 실제 \(day.hours.formatted())시간").font(.caption).foregroundStyle(.secondary)
                }
                BusyTimeStatusView(display: busy, timeZone: interval.timeZone, onRetry: onRetryBusy)
                ScrollViewReader { proxy in
                    ScrollView(.vertical) {
                        EventTimelineGrid(day: day, title: title, hourHeight: hourHeight, gutter: min(92, gutter), busy: busy, timeZone: interval.timeZone)
                    }
                    .frame(height: min(520, max(320, hourHeight * 5)))
                    .background(.background, in: RoundedRectangle(cornerRadius: 16))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .onAppear { scroll(proxy, day); onSelectDay?(day) }
                    .onChange(of: busy.status) { _, status in if status == .ready { scroll(proxy, day) } }
                    .onChange(of: selectedDate) { _, _ in scroll(proxy, day); onSelectDay?(day) }
                }
                .accessibilityLabel("\(title) 일간 시간표")
            }
        }
    }
    private func scroll(_ proxy: ScrollViewProxy, _ day: EventTimelineInterval.Day) {
        let tick = day.ticks.last { $0.offset <= max(0, day.offset - 1) }
        if let tick { proxy.scrollTo(tick.id, anchor: .top) }
    }

}

#Preview("일간 시간표") {
    let start = Date(timeIntervalSince1970: 1_788_393_600)
    EventDayTimeline(interval: EventTimelineInterval(start: start, end: start.addingTimeInterval(90000), timeZone: TimeZone(identifier: "Asia/Seoul")!)!, title: "행사 일정")
        .padding()
}
