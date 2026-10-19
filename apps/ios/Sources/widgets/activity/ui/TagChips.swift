import SwiftUI

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
