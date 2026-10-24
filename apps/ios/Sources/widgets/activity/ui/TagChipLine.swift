import SwiftUI

/// 발견 카드·펼친 활동 행의 태그 줄 (contract "태그 아이콘+라벨·분야 1개·자동 펼침 없음" 2026-10-24): 아이콘+글자 칩 한 줄에 분야 1개(민트 바탕·청록 글자)
/// 다음 성격 칩(보조색 바탕). 줄바꿈하지 않고 들어가는 만큼만 보이며 나머지는 `+n`. 카드가 접근성 라벨을 합쳐 읽을 때는 `TagChipLine.label`을 쓴다.
struct TagChipLine: View {
    let field: Tag?
    let traits: [Tag]
    private var tags: [Tag] { (field.map { [$0] } ?? []) + traits }
    /// The chips' text as spoken: every tag name, hidden ones included.
    static func label(field: Tag?, traits: [Tag]) -> String? {
        let names = ((field.map { [$0] } ?? []) + traits).map(\.name)
        return names.isEmpty ? nil : names.joined(separator: ", ")
    }
    var body: some View {
        let tags = tags
        if !tags.isEmpty {
            // The longest prefix that fits on one line, the rest counted as `+n`.
            ViewThatFits(in: .horizontal) {
                ForEach((0...tags.count).reversed(), id: \.self) { shown in
                    HStack(spacing: 6) {
                        ForEach(tags.prefix(shown)) { tag in DearbyTagChip(title: tag.name, nameEn: tag.nameEn, accent: tag.id == field?.id) }
                        if shown < tags.count { Text("+\(tags.count - shown)").dearbyTagChip() }
                    }.fixedSize()
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .ignore).accessibilityLabel(Self.label(field: field, traits: traits) ?? "")
        }
    }
}
