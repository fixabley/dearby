import SwiftUI

/// 검색 칸. `expansion` 0은 돋보기만 보이는 얇은 막대, 1은 입력 칸이다. 검색어가 있거나 입력 중이면 항상 펼친다.
/// 동작 줄이기에서는 중간 크기 없이 바로 바뀐다. 줄어든 막대는 접근성 도구에서 `검색` 버튼으로 읽힌다.
struct DearbySearchField: View {
    let prompt: String
    @Binding var text: String
    var identifier = "search"
    var expansion: Double = 1
    var onExpand: () -> Void = {}
    /// Tells the page when the field gains or loses the keyboard (the discovery header stays open while typing).
    var onFocusChange: (Bool) -> Void = { _ in }
    @FocusState private var focused: Bool
    @State private var focusOnAppear = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private var progress: Double {
        if !text.isEmpty || focused || focusOnAppear { return 1 }
        let value = min(max(expansion, 0), 1)
        return reduceMotion ? (value >= 0.5 ? 1 : 0) : value
    }
    var body: some View {
        if progress >= 1 { field } else { bar }
    }
    private var field: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(DearbyStyle.quiet).accessibilityHidden(true)
            TextField(prompt, text: $text).font(.dearby(.body)).focused($focused)
                .accessibilityLabel(prompt).accessibilityIdentifier(identifier)
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(DearbyStyle.quiet).frame(width: 44, height: 44)
                }.buttonStyle(.plain).accessibilityLabel("검색어 지우기")
            }
        }.padding(.leading, 14).padding(.trailing, text.isEmpty ? 14 : 0).frame(minHeight: 48)
            .background(DearbyStyle.muted, in: Capsule())
            .onAppear { if focusOnAppear { focused = true; focusOnAppear = false } }
            .onChange(of: focused) { _, value in onFocusChange(value) }
    }
    private var bar: some View {
        let height = 28 + 20 * progress
        return Button { focusOnAppear = true; onExpand() } label: {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").imageScale(progress < 0.5 ? .small : .medium)
                Text(prompt).font(.dearby(.body)).lineLimit(1).opacity(max(0, progress * 2 - 1))
                Spacer(minLength: 0)
            }.foregroundStyle(DearbyStyle.quiet).padding(.horizontal, 14)
                .frame(maxWidth: .infinity).frame(height: height).background(DearbyStyle.muted, in: Capsule())
                .padding(.vertical, max(0, (44 - height) / 2)).contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityElement(children: .ignore).accessibilityLabel("검색")
            .accessibilityAddTraits(.isButton).accessibilityIdentifier(identifier)
    }
}

/// 목록 맨 위에서 아래로 당기면 검색 칸을 펼치고, 목록을 위로 밀면 줄인다. 손을 떼면 0.5를 기준으로 0 또는 1로 맞춘다.
struct DearbySearchReveal: ViewModifier {
    @Binding var expansion: Double
    var distance: CGFloat = 56
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func body(content: Content) -> some View {
        content.scrollBounceBehavior(.always, axes: .vertical)
            .onScrollGeometryChange(for: CGFloat.self) { $0.contentOffset.y + $0.contentInsets.top } action: { old, new in
                if new < 0 {
                    expansion = max(expansion, min(1, -new / distance))
                } else if new > old, expansion > 0 {
                    expansion = max(0, expansion - (new - old) / distance)
                }
            }
            .onScrollPhaseChange { _, phase in
                guard phase != .interacting, expansion > 0, expansion < 1 else { return }
                let target: Double = expansion >= 0.5 ? 1 : 0
                if reduceMotion { expansion = target } else { withAnimation(.snappy) { expansion = target } }
            }
    }
}

extension View {
    func dearbySearchReveal(_ expansion: Binding<Double>) -> some View { modifier(DearbySearchReveal(expansion: expansion)) }
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
            DearbySearchField(prompt: "이름, 직무, 활동으로 검색", text: $collapsedQuery, expansion: 0)
            DearbySearchField(prompt: "이름, 직무, 활동으로 검색", text: $collapsedQuery, expansion: 0.7)
            DearbyInlineField(label: "이름", text: $name, editing: editing, font: .dearby(.title2).bold())
            DearbyInlineField(label: "소개", text: $introduction, editing: editing, prompt: "한 줄 소개를 적어 주세요", multiline: true)
        }.padding(20).background(Color(.systemBackground))
    }
}

#Preview("편집") { ScrollView { DearbyControlsGallery() } }
#Preview("읽기") { ScrollView { DearbyControlsGallery(editing: false) } }
#endif
