import SwiftUI

struct NoticeApplicationView: View {
    let summary: String
    let onAddToCalendar: (() -> Void)?
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("신청").font(.headline).accessibilityAddTraits(.isHeader)
            MetadataRow(systemImage: "calendar", text: summary, accessibilityText: "신청 기간, \(summary)")
            if let onAddToCalendar {
                CalendarAddButton(onAdd: onAddToCalendar)
                    .accessibilityLabel("신청 기간 캘린더에 추가")
            }
        }
    }
}
