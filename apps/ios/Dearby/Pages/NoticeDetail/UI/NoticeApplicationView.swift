import SwiftUI

struct NoticeApplicationView: View {
    let time: EventPeriodPresentation
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
            DisclosureGroup("원문 신청 안내") {
                Text(original).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }
}
