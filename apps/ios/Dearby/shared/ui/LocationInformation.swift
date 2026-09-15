import SwiftUI

/// Pure strings plus a caller-owned action slot. Names and addresses wrap independently.
struct LocationInformation<Action: View>: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    let name: String
    let detail: String?
    @ViewBuilder let action: () -> Action

    var body: some View {
        VStack(alignment: .leading, spacing: NativeSpacing.related) {
            HStack(alignment: .top, spacing: NativeSpacing.related) {
                if !typeSize.isAccessibilitySize {
                    Image(systemName: "mappin.and.ellipse").foregroundStyle(.secondary).accessibilityHidden(true)
                }
                VStack(alignment: .leading, spacing: NativeSpacing.compact) {
                    Text(name).font(.body)
                    if let detail { Text(detail).font(.subheadline).foregroundStyle(.secondary) }
                }
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                if !typeSize.isAccessibilitySize { action() }
            }
            if typeSize.isAccessibilitySize { action() }
        }
    }
}
