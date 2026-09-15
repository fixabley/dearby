import SwiftUI

struct NoticePreviewLabel: View {
    let title: String
    let category: String
    let contextNames: String

    var body: some View {
        VStack(alignment: .leading, spacing: NativeSpacing.compact) {
            Text(title).font(.body)
            NoticeClassificationView(category: category, contextNames: contextNames)
        }
        .frame(minWidth: 44, minHeight: 44, alignment: .leading)
    }
}
