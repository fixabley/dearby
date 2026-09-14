import SwiftUI

/// Text field owned by the NoticeDetail slice.
struct NoticeDetailField: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value)
        }
    }
}
