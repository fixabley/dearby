import SwiftUI

struct NoticeApplicationView: View {
    let summary: String
    let onAddToCalendar: (() -> Void)?
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            NoticeDetailField(title: "신청 기간", value: summary)
            if let onAddToCalendar { CalendarAddButton(onAdd: onAddToCalendar) }
        }
    }
}
