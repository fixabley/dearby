import SwiftUI

/// 검색 칸: 시스템 검색 모양(contract "폼은 플랫폼 기본 컴포넌트" 2026-10-19 밤) — 돋보기, `TextField`, 지우기 버튼을 둥근 `tertiarySystemFill` 바탕
/// 높이 44에 둔다. 페이지가 키보드를 내리거나 포커스를 읽어야 하면 `focus`로 자기 `@FocusState`를 넘긴다.
struct DearbySearchField: View {
    let prompt: String
    @Binding var text: String
    var identifier = "search"
    var focus: FocusState<Bool>.Binding?
    @FocusState private var ownFocus: Bool
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary).accessibilityHidden(true)
            TextField(prompt, text: $text).font(.dearby(.body)).focused(focus ?? $ownFocus)
                .accessibilityLabel(prompt).accessibilityIdentifier(identifier)
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary).frame(width: 44, height: 44)
                }.buttonStyle(.plain).accessibilityLabel("검색어 지우기")
            }
        }.padding(.leading, 10).padding(.trailing, text.isEmpty ? 10 : 0).frame(height: 44)
            .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

#if DEBUG
struct DearbyControlsGallery: View {
    @State var query = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            DearbySearchField(prompt: "이름, 직무, 활동으로 검색", text: $query)
        }.padding(20).background(Color(.systemBackground))
    }
}

#Preview("검색") { ScrollView { DearbyControlsGallery() } }
#endif
