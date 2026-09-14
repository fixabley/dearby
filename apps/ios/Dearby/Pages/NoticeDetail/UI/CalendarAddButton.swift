import SwiftUI

struct CalendarAddButton: View {
    let onAdd: () -> Void
    var body: some View {
        Button(action: onAdd) {
            Label("캘린더에 추가", systemImage: "calendar.badge.plus")
                .frame(minHeight: 44, alignment: .leading)
        }
        .buttonStyle(.borderless)
    }
}
