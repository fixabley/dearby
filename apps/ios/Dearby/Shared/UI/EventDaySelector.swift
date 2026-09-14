import SwiftUI

struct EventDaySelector: View {
    @Binding var selection: Date
    let range: ClosedRange<Date>
    let timeZone: TimeZone
    let calendar: Calendar
    let title: String
    let previousDate: Date?
    let nextDate: Date?
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let layout = dynamicTypeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading)) : AnyLayout(HStackLayout())
        layout {
            DatePicker("날짜", selection: $selection, in: range, displayedComponents: .date)
                .datePickerStyle(.compact)
                .environment(\.timeZone, timeZone)
                .environment(\.calendar, calendar)
                .environment(\.locale, Locale(identifier: "ko_KR"))
                .accessibilityLabel("\(title) 미리보기 날짜")
            HStack {
                Button { if let previousDate { selection = previousDate } } label: {
                    Label("이전 날짜", systemImage: "chevron.left").labelStyle(.iconOnly).frame(minWidth: 44, minHeight: 44)
                }.buttonStyle(.borderless).disabled(previousDate == nil)
                Button { if let nextDate { selection = nextDate } } label: {
                    Label("다음 날짜", systemImage: "chevron.right").labelStyle(.iconOnly).frame(minWidth: 44, minHeight: 44)
                }.buttonStyle(.borderless).disabled(nextDate == nil)
            }
        }
    }
}
