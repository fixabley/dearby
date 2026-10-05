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

struct DearbySearchField: View {
    let prompt: String
    @Binding var text: String
    var identifier = "search"
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(DearbyStyle.quiet).accessibilityHidden(true)
            TextField(prompt, text: $text).font(.body).accessibilityLabel(prompt).accessibilityIdentifier(identifier)
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(DearbyStyle.quiet).frame(width: 44, height: 44)
                }.buttonStyle(.plain).accessibilityLabel("검색어 지우기")
            }
        }.padding(.leading, 14).padding(.trailing, text.isEmpty ? 14 : 0).frame(minHeight: 48)
            .background(DearbyStyle.muted, in: Capsule())
    }
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
    @State var chosen: Set<String> = ["conference"]
    @State var name = "김지민"
    @State var introduction = ""
    var editing = true
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            DearbySegments(labels: ["저장한 활동", "신청한 활동"], selection: $segment)
            DearbySearchField(prompt: "이름, 직무, 활동으로 검색", text: $query)
            DearbySectionHeader(title: "Dearby 개발자 컨퍼런스", count: 3)
            DearbyChoiceChips(items: [.init(id: "conference", title: "Dearby 개발자 컨퍼런스"), .init(id: "camp", title: "Dearby 메이커 캠프"),
                                      .init(id: "meetup", title: "Dearby 커뮤니티 밋업")], selection: $chosen, label: "함께 보낼 활동")
            DearbyInlineField(label: "이름", text: $name, editing: editing, font: .title2.bold())
            DearbyInlineField(label: "소개", text: $introduction, editing: editing, prompt: "한 줄 소개를 적어 주세요", multiline: true)
        }.padding(20).background(.white)
    }
}

#Preview("편집") { ScrollView { DearbyControlsGallery() } }
#Preview("읽기") { ScrollView { DearbyControlsGallery(editing: false) } }
#endif
