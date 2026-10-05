import SwiftUI

struct DearbyChoice: Identifiable {
    let id: String
    let title: String
}

struct DearbySectionHeader: View {
    let title: String
    let count: Int
    var body: some View {
        HStack(spacing: 8) {
            Text(title).font(.headline).fixedSize(horizontal: false, vertical: true)
            Text("\(count)").font(.caption.weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                .padding(.horizontal, 8).padding(.vertical, 2).background(DearbyStyle.mint, in: Capsule())
            Spacer(minLength: 0)
        }.padding(.top, 8)
            .accessibilityElement(children: .ignore).accessibilityLabel("\(title), \(count)개")
            .accessibilityAddTraits(.isHeader)
    }
}

/// 검색 칸. `expansion` 0은 돋보기만 보이는 얇은 막대, 1은 입력 칸이다. 검색어가 있거나 입력 중이면 항상 펼친다.
/// 동작 줄이기에서는 중간 크기 없이 바로 바뀐다. 줄어든 막대는 접근성 도구에서 `검색` 버튼으로 읽힌다.
struct DearbySearchField: View {
    let prompt: String
    @Binding var text: String
    var identifier = "search"
    var expansion: Double = 1
    var onExpand: () -> Void = {}
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
            TextField(prompt, text: $text).font(.body).focused($focused)
                .accessibilityLabel(prompt).accessibilityIdentifier(identifier)
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(DearbyStyle.quiet).frame(width: 44, height: 44)
                }.buttonStyle(.plain).accessibilityLabel("검색어 지우기")
            }
        }.padding(.leading, 14).padding(.trailing, text.isEmpty ? 14 : 0).frame(minHeight: 48)
            .background(DearbyStyle.muted, in: Capsule())
            .onAppear { if focusOnAppear { focused = true; focusOnAppear = false } }
    }
    private var bar: some View {
        let height = 28 + 20 * progress
        return Button { focusOnAppear = true; onExpand() } label: {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").imageScale(progress < 0.5 ? .small : .medium)
                Text(prompt).font(.body).lineLimit(1).opacity(max(0, progress * 2 - 1))
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

struct DearbyChoiceChips: View {
    let items: [DearbyChoice]
    @Binding var selection: Set<String>
    let label: String
    var body: some View {
        DearbyFlowLayout(spacing: 8) {
            ForEach(items) { item in
                let selected = selection.contains(item.id)
                Button {
                    if !selection.insert(item.id).inserted { selection.remove(item.id) }
                } label: {
                    HStack(spacing: 6) {
                        if selected { Image(systemName: "checkmark").accessibilityHidden(true) }
                        Text(item.title).multilineTextAlignment(.leading)
                    }.font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 14).padding(.vertical, 8).frame(minHeight: 44)
                        .foregroundStyle(selected ? .white : DearbyStyle.quiet)
                        .background(selected ? DearbyStyle.teal : DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 22))
                }.buttonStyle(.plain).accessibilityAddTraits(selected ? .isSelected : [])
            }
        }.accessibilityElement(children: .contain).accessibilityLabel(label)
    }
}

struct DearbyInlineField: View {
    let label: String
    @Binding var text: String
    let editing: Bool
    var prompt = ""
    var multiline = false
    var font: Font = .body
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
            } else {
                Text(text.isEmpty ? prompt : text).foregroundStyle(text.isEmpty ? DearbyStyle.quiet : DearbyStyle.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityLabel("\(label), \(text.isEmpty ? "비어 있음" : text)")
            }
        }.font(font).padding(.horizontal, 8).padding(.vertical, 6)
            .background(editing ? DearbyStyle.muted : .clear, in: RoundedRectangle(cornerRadius: 8))
    }
}

/// 받은 명함의 "함께한 활동 · ○○ 외 N개" 라벨. 보낸 사람이 고른 정보라 확인 아이콘 없이 글자만 쓰고, 긴 활동 이름만 말줄임한다.
/// 첫 활동(일정이 가장 이른 것) 선택은 호출하는 쪽 모델이 정한다.
struct DearbyTogetherActivityLabel: View {
    let title: String
    var otherCount = 0
    private var suffix: String { otherCount > 0 ? " 외 \(otherCount)개" : "" }
    var body: some View {
        HStack(spacing: 0) {
            Text("함께한 활동 · ").foregroundStyle(DearbyStyle.quiet).lineLimit(1).fixedSize()
            Text(title).fontWeight(.semibold).foregroundStyle(DearbyStyle.ink).lineLimit(1).truncationMode(.tail)
            Text(suffix).foregroundStyle(DearbyStyle.quiet).lineLimit(1).fixedSize()
        }.font(.subheadline)
            .accessibilityElement(children: .ignore).accessibilityLabel("함께한 활동, \(title)\(suffix)")
    }
}

/// 칩처럼 폭이 다른 항목을 줄바꿈해 배치한다. 큰 글씨에서는 한 줄에 한 개만 놓일 수 있다.
struct DearbyFlowLayout: Layout {
    var spacing: CGFloat = 8
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        return CGSize(width: rows.map(\.width).max() ?? 0, height: rows.last.map { $0.y + $0.height } ?? 0)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.init(width: bounds.width, height: nil))
                subviews[index].place(at: CGPoint(x: x, y: bounds.minY + row.y), proposal: .init(size))
                x += size.width + spacing
            }
        }
    }
    private func arrange(width: CGFloat, subviews: Subviews) -> [(indices: [Int], y: CGFloat, width: CGFloat, height: CGFloat)] {
        var rows: [(indices: [Int], y: CGFloat, width: CGFloat, height: CGFloat)] = []
        var current: [Int] = [], x: CGFloat = 0, y: CGFloat = 0, height: CGFloat = 0
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.init(width: width, height: nil))
            if !current.isEmpty && x + size.width > width {
                rows.append((current, y, x - spacing, height))
                y += height + spacing; current = []; x = 0; height = 0
            }
            current.append(index); x += size.width + spacing; height = max(height, size.height)
        }
        if !current.isEmpty { rows.append((current, y, x - spacing, height)) }
        return rows
    }
}

#if DEBUG
/// 미리보기와 캡처 테스트가 함께 쓰는 예시 배치. 화면 이동이나 예시 데이터 상태는 담지 않는다.
struct DearbyControlsGallery: View {
    @State var segment = 0
    @State var query = ""
    @State var collapsedQuery = ""
    @State var chosen: Set<String> = ["conference"]
    @State var name = "김지민"
    @State var introduction = ""
    var editing = true
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            DearbySegments(labels: ["저장한 활동", "신청한 활동"], selection: $segment)
            DearbySearchField(prompt: "이름, 직무, 활동으로 검색", text: $query)
            DearbySearchField(prompt: "이름, 직무, 활동으로 검색", text: $collapsedQuery, expansion: 0)
            DearbySearchField(prompt: "이름, 직무, 활동으로 검색", text: $collapsedQuery, expansion: 0.7)
            DearbySectionHeader(title: "Dearby 개발자 컨퍼런스", count: 3)
            DearbyChoiceChips(items: [.init(id: "conference", title: "Dearby 개발자 컨퍼런스"), .init(id: "camp", title: "Dearby 메이커 캠프"),
                                      .init(id: "meetup", title: "Dearby 커뮤니티 밋업")], selection: $chosen, label: "함께 보낼 활동")
            DearbyTogetherActivityLabel(title: "Dearby 개발자 컨퍼런스")
            DearbyTogetherActivityLabel(title: "Dearby 메이커 캠프 여름 시즌 집중 프로그램", otherCount: 2)
            DearbyInlineField(label: "이름", text: $name, editing: editing, font: .title2.bold())
            DearbyInlineField(label: "소개", text: $introduction, editing: editing, prompt: "한 줄 소개를 적어 주세요", multiline: true)
        }.padding(20).background(.white)
    }
}

#Preview("편집") { ScrollView { DearbyControlsGallery() } }
#Preview("읽기") { ScrollView { DearbyControlsGallery(editing: false) } }
#endif
