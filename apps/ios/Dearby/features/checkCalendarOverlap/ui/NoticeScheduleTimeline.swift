import SwiftUI

/// Couples a notice phase to its ephemeral selected-day query and retry UI.
struct NoticeScheduleTimeline: View {
    let state: NoticeScheduleState
    let session: BusyCalendarSession
    let index: Int

    var body: some View {
        EventTimeRows(lines: state.time.lines, note: state.time.note)
        if let interval = state.time.timeline {
            CalendarOverlapTimeline(interval: interval, title: state.title,
                busy: session.days[index] ?? .hidden, onSelectDay: { day in
                    session.select(id: index, day: DateInterval(start: day.start, end: day.end),
                        activity: DateInterval(start: day.clippedStart, end: day.clippedEnd))
                }, onRetry: session.refresh)
        } else {
            Text("시작·종료 시각이 확인되면 시간표를 표시합니다.").font(.footnote).foregroundStyle(.secondary)
        }
    }
}
