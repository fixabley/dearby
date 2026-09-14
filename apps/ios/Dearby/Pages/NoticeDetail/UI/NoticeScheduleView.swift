import SwiftUI

struct NoticeScheduleView: View {
    let summary: String
    let onAddToCalendar: (() -> Void)?
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            NoticeDetailField(title: "활동 일정", value: summary)
            if let onAddToCalendar { CalendarAddButton(onAdd: onAddToCalendar) }
        }
    }
}
