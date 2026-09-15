import SwiftUI

/// A supplied, validated URL; no fetching, preview request, or share action.
struct ExternalLinkCard: View {
    let url: URL
    let label: String
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let layout = dynamicTypeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: NativeSpacing.related)) : AnyLayout(HStackLayout(spacing: NativeSpacing.related))
        layout {
            Label(url.host ?? url.absoluteString, systemImage: "link")
                .font(.body).fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityLabel("\(label): \(url.absoluteString)")
            Link("열기", destination: url)
                .buttonStyle(.borderedProminent).buttonBorderShape(.capsule)
                .frame(minWidth: 44, minHeight: 44)
                .accessibilityLabel("\(label) 열기: \(url.host ?? url.absoluteString)")
        }
        .padding(NativeSpacing.related)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 16))
    }
}

#Preview("링크 · 큰 글자") {
    ExternalLinkCard(url: URL(string: "https://cieat.cbnu.ac.kr/")!, label: "신청 링크")
        .padding().environment(\.dynamicTypeSize, .accessibility5)
}
