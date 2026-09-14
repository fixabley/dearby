import SwiftUI

struct CalendarAddButton: View {
    let onAdd: () -> Void
    var body: some View {
        Button(action: onAdd) {
            Label("캘린더에 추가", systemImage: "calendar.badge.plus")
                .labelStyle(.iconOnly)
                .frame(minWidth: 44, minHeight: 44)
        }
        .buttonStyle(.borderless)
    }
}
