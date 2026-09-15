import SwiftUI

struct NoticeScheduleBody<Action: View, Content: View>: View {
    let title: String
    @ViewBuilder let action: () -> Action
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: NativeSpacing.content) {
            HStack(alignment: .top) {
                Text(title).font(.headline).accessibilityAddTraits(.isHeader)
                Spacer()
                action()
            }
            content()
        }
    }
}
