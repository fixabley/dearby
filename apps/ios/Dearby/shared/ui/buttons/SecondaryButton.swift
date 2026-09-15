import SwiftUI

struct SecondaryButton<Content: View>: View {
    let action: () -> Void
    @ViewBuilder let label: () -> Content

    var body: some View {
        Button(action: action) {
            label()
                .fixedSize(horizontal: false, vertical: true)
        }
            .controlSize(.large)
            .buttonStyle(.bordered)
    }
}
