import SwiftUI

struct NoticeApplicationView: View {
    let time: EventPeriodPresentation
    let title: String
    let original: String
    let url: URL?
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
            EventTimeRows(lines: time.lines, note: time.note)
            if let url { ExternalLinkCard(url: url, label: "신청 링크") }
            if let interval = time.timeline {
                EventDayTimeline(interval: interval, title: "[신청] \(title)")
            } else {
                Text("시작·종료 시각이 확인되면 시간표를 표시합니다.").font(.footnote).foregroundStyle(.secondary)
            }
            DisclosureGroup("원문 신청 안내") {
                Text(original).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }
}
