import SwiftUI

/// Icon-labelled fact owned by the NoticeDetail slice.
struct NoticeIdentityFact: View {
    let label: String
    let value: String
    let icon: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label(label, systemImage: icon).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.subheadline.weight(.medium))
        }
    }
}
