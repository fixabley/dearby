import SwiftUI

/// Native alternatives let large-text readers change cards without fighting nested vertical scrolling.
struct DiscoveryPageControls: View {
    let position: String
    let canPrevious: Bool
    let canNext: Bool
    let onPrevious: () -> Void
    let onNext: () -> Void
    var body: some View {
        HStack {
            Button("이전 공고", systemImage: "chevron.up", action: onPrevious)
                .labelStyle(.iconOnly).frame(minWidth: 44, minHeight: 44).disabled(!canPrevious)
            Spacer()
            Text(position).font(.caption).accessibilityLabel("현재 공고 \(position)")
            Spacer()
            Button("다음 공고", systemImage: "chevron.down", action: onNext)
                .labelStyle(.iconOnly).frame(minWidth: 44, minHeight: 44).disabled(!canNext)
        }.padding(.horizontal)
    }
}
