import SwiftUI

/// 발견 검색창 아래의 태그 칩 줄(contract "태그(얻을 수 있는 것)"): 사전의 태그를 가로로 스크롤하며 여러 개를 고른다.
/// 고른 칩은 청록 채움, 나머지는 민트 바탕. 접근성에서는 각 칩이 선택 상태를 가진 버튼으로 읽힌다.
struct TagChipRow: View {
    let tags: [Tag]
    let selected: Set<Int64>
    let toggle: (Int64) -> Void
    var body: some View {
        if !tags.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(tags) { tag in
                        let on = selected.contains(tag.id)
                        Button { toggle(tag.id) } label: {
                            Text(tag.name).font(.dearby(.caption).weight(.semibold))
                                .foregroundStyle(on ? .white : DearbyStyle.teal)
                                .padding(.horizontal, 12).frame(minHeight: 32)
                                .background(on ? DearbyStyle.teal : DearbyStyle.mint, in: Capsule())
                        }.buttonStyle(.plain)
                            .accessibilityAddTraits(on ? [.isSelected] : [])
                            .accessibilityHint(on ? "누르면 이 태그 조건을 빼요" : "누르면 이 태그가 있는 프로그램만 보여요")
                            .accessibilityIdentifier("tag-\(tag.id)")
                    }
                }.padding(.vertical, 2)
            }.accessibilityLabel("얻을 수 있는 것 태그")
        }
    }
}

/// 활동·프로그램에 붙은 태그 칩 몇 개(`limit`까지, 넘치면 `+n`). 조용한 배지 모양.
struct TagChips: View {
    let tags: [Tag]
    var limit: Int = .max
    var body: some View {
        if !tags.isEmpty {
            HStack(spacing: 6) {
                ForEach(tags.prefix(limit)) { tag in DearbyBadge(title: tag.name, quiet: true) }
                if tags.count > limit { DearbyBadge(title: "+\(tags.count - limit)", quiet: true) }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("태그 " + tags.map(\.name).joined(separator: ", "))
        }
    }
}
