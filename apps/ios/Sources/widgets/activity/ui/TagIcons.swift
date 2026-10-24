import SwiftUI

/// 발견 카드의 태그 아이콘 (contract "분야·성격 태그와 아이콘" 2026-10-24). 분야는 로고 밑 세로 배지(지름 22, 민트 바탕·청록 아이콘, 간격 4),
/// 성격은 글자 없는 아이콘 줄(16, 보조색, 간격 8). 카드가 접근성 라벨을 합쳐 읽을 때는 `TraitTagIcons.label`을 쓴다.
struct FieldTagBadges: View {
    let tags: [Tag]
    var body: some View {
        VStack(spacing: 4) {
            ForEach(tags) { tag in
                Image(systemName: DearbyTagSymbol.name(tag.nameEn)).font(.dearby(fixedSize: 11, weight: .semibold))
                    .foregroundStyle(DearbyStyle.teal).frame(width: 22, height: 22).background(DearbyStyle.mint, in: Circle())
                    .accessibilityLabel("분야: " + tag.name)
            }
        }
    }
}

struct TraitTagIcons: View {
    let tags: [Tag]
    static func label(_ tags: [Tag]) -> String? { tags.isEmpty ? nil : "얻을 수 있는 것: " + tags.map(\.name).joined(separator: ", ") }
    var body: some View {
        if let label = Self.label(tags) {
            HStack(spacing: 8) {
                ForEach(tags) { tag in Image(systemName: DearbyTagSymbol.name(tag.nameEn)).font(.dearby(fixedSize: 16)) }
            }
            .foregroundStyle(.secondary)
            .accessibilityElement(children: .ignore).accessibilityLabel(label)
        }
    }
}
