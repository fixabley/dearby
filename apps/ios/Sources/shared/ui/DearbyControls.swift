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

struct DearbyInlineField: View {
    let label: String
    @Binding var text: String
    let editing: Bool
    var prompt = ""
    var multiline = false
    var font: Font = .dearby(.body)
    /// 퍼널처럼 화면에 입력이 하나뿐일 때 나타나자마자 키보드를 연다.
    var autofocus = false
    @FocusState private var focused: Bool
    var body: some View {
        Group {
            if editing {
                // 시스템 placeholder는 한 줄로 잘리므로 큰 글씨에서도 줄바꿈되는 안내 문구를 아래에 겹친다.
                ZStack(alignment: .topLeading) {
                    if text.isEmpty {
                        Text(prompt.isEmpty ? label : prompt).foregroundStyle(DearbyStyle.quiet)
                            .frame(maxWidth: .infinity, alignment: .leading).accessibilityHidden(true)
                    }
                    TextField("", text: $text, axis: multiline ? .vertical : .horizontal)
                        .lineLimit(multiline ? 1...6 : 1...1)
                        .accessibilityLabel(label).accessibilityHint(prompt).focused($focused)
                }.contentShape(Rectangle()).onTapGesture { focused = true }
                .background(alignment: .bottom) { Rectangle().fill(DearbyStyle.teal).frame(height: 1).offset(y: 6) }
                .onAppear { if autofocus { Task { @MainActor in try? await Task.sleep(for: .milliseconds(80)); focused = true } } }
            } else {
                Text(text.isEmpty ? prompt : text).foregroundStyle(text.isEmpty ? DearbyStyle.quiet : DearbyStyle.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityLabel("\(label), \(text.isEmpty ? "비어 있음" : text)")
            }
        }.font(font).padding(.horizontal, 8).padding(.vertical, 6)
            .background(editing ? DearbyStyle.muted : .clear, in: RoundedRectangle(cornerRadius: 8))
    }
}

#if DEBUG
/// 미리보기와 캡처 테스트가 함께 쓰는 예시 배치. 화면 이동이나 예시 데이터 상태는 담지 않는다.
struct DearbyControlsGallery: View {
    @State var query = ""
    @State var collapsedQuery = ""
    @State var name = "김지민"
    @State var introduction = ""
    var editing = true
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            DearbySearchField(prompt: "이름, 직무, 활동으로 검색", text: $query)
            DearbySearchField(prompt: "이름, 직무, 활동으로 검색", text: $collapsedQuery)
            DearbyInlineField(label: "이름", text: $name, editing: editing, font: .dearby(.title2).bold())
            DearbyInlineField(label: "소개", text: $introduction, editing: editing, prompt: "한 줄 소개를 적어 주세요", multiline: true)
        }.padding(20).background(Color(.systemBackground))
    }
}

#Preview("편집") { ScrollView { DearbyControlsGallery() } }
#Preview("읽기") { ScrollView { DearbyControlsGallery(editing: false) } }
#endif
