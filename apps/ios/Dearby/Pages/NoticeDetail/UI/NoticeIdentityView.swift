import SwiftUI

/// Identity panel owned by the NoticeDetail slice.
struct NoticeIdentityView: View {
    let detail: ActivityDetail

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if let target = detail.organizationPath.last {
                NoticeIdentityFact(label: "관심 조직", value: target.name, icon: "heart")
                let ancestors = detail.organizationPath.dropLast()
                if !ancestors.isEmpty {
                    NoticeIdentityFact(label: "상위 조직", value: ancestors.map(\.name).joined(separator: " › "), icon: "building.2")
                }
                Text("이 공고에서 관심 표시하면 \(target.name)이 저장돼요.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            NoticeIdentityFact(label: "활동 분류", value: detail.categorySummary, icon: "square.grid.2x2")
            ForEach(Array(detail.contexts.enumerated()), id: \.offset) { _, context in
                if let name = context.organizationName {
                    NoticeIdentityFact(label: context.reference.label, value: name, icon: "building.2")
                }
            }
            if let edition = detail.edition {
                NoticeIdentityFact(label: "회차", value: "제\(edition)회", icon: "calendar")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.quaternary.opacity(0.4), in: RoundedRectangle(cornerRadius: 16))
        .accessibilityIdentifier("identity.\(detail.id)")
    }
}
