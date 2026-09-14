import SwiftUI

/// Shared by discovery cards and the linked notices in favorites.
struct NoticeClassificationView: View {
    let category: String
    let contextNames: String

    var body: some View {
        Text([category, contextNames].filter { !$0.isEmpty }.joined(separator: " · "))
            .font(.caption).foregroundStyle(.secondary)
    }
}
