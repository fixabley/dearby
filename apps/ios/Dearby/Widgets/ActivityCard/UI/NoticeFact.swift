import SwiftUI

/// Display helper owned by the ActivityCard slice.
struct NoticeFact: View {
    let label: String
    let value: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.subheadline).lineLimit(2)
        }
    }
}
