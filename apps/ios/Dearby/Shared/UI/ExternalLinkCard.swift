import SwiftUI

/// A supplied, validated URL; no fetching, preview request, or share action.
struct ExternalLinkCard: View {
    let url: URL
    let label: String

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: NativeSpacing.related) { domain; open }
            VStack(alignment: .leading, spacing: NativeSpacing.related) { domain; open }
        }
        .padding(NativeSpacing.related)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 16))
    }

    private var domain: some View {
        Label(url.host ?? url.absoluteString, systemImage: "link")
            .font(.body).fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityLabel("\(label): \(url.absoluteString)")
    }
    private var open: some View {
        Link("열기", destination: url)
            .buttonStyle(.borderedProminent).buttonBorderShape(.capsule)
            .frame(minWidth: 44, minHeight: 44)
            .accessibilityLabel("\(label) 열기: \(url.host ?? url.absoluteString)")
    }
}

#Preview("링크 · 큰 글자") {
    ExternalLinkCard(url: URL(string: "https://cieat.cbnu.ac.kr/")!, label: "신청 링크")
        .padding().environment(\.dynamicTypeSize, .accessibility5)
}
