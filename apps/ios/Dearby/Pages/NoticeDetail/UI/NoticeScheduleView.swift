import SwiftUI

struct NoticeScheduleView: View {
    let summary: String
    let onAddToCalendar: (() -> Void)?
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            InformationRow(title: "활동 일정", value: summary)
            if let onAddToCalendar {
                CalendarAddButton(onAdd: onAddToCalendar)
                    .accessibilityLabel("활동 일정 캘린더에 추가: \(summary)")
            }
        }
    }
}
