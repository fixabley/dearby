import SwiftUI

struct NoticeApplicationView: View {
    let state: NoticeApplicationState
    let onAddToCalendar: (() -> Void)?
    var body: some View {
        VStack(alignment: .leading, spacing: NativeSpacing.content) {
            HStack(alignment: .top) {
                Text("신청").font(.headline).accessibilityAddTraits(.isHeader)
                Spacer()
                if let onAddToCalendar {
                    CalendarAddButton(onAdd: onAddToCalendar).accessibilityLabel("신청 기간 캘린더에 추가")
                }
            }
            EventTimeRows(lines: state.time.lines, note: state.time.note)
            if let url = state.url { ExternalLinkCard(url: url, label: "신청 링크") }
            if let interval = state.time.timeline {
                EventDayTimeline(interval: interval, title: "[신청] \(state.title)")
            } else {
                Text("시작·종료 시각이 확인되면 시간표를 표시합니다.").font(.footnote).foregroundStyle(.secondary)
            }
            DisclosureGroup("원문 신청 안내") {
                Text(state.original).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }
}
