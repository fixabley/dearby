import SwiftUI

/// A wrapping native icon/value pair. The caller supplies domain meaning.
struct MetadataRow: View {
    let systemImage: String
    let text: String
    var accessibilityText: String? = nil

    var body: some View {
        Label {
            Text(text).fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: systemImage).foregroundStyle(.secondary)
        }
        .font(.subheadline)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText ?? text)
    }
}
