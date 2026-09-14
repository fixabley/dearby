import SwiftUI

/// Identity panel owned by the NoticeDetail slice.
struct NoticeIdentityView: View {
    let state: NoticeDetailState

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if let target = state.organizationName {
                NoticeIdentityFact(label: "관심 조직", value: target, icon: "heart")
                let ancestors = state.organizationPath
                if !ancestors.isEmpty {
                    NoticeIdentityFact(label: "상위 조직", value: ancestors.joined(separator: " › "), icon: "building.2")
                }
                Text("이 공고에서 관심 표시하면 \(target)이 저장돼요.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            NoticeIdentityFact(label: "활동 분류", value: state.categorySummary, icon: "square.grid.2x2")
            ForEach(Array(state.contexts.enumerated()), id: \.offset) { _, context in
                if let name = context.organizationName {
                    NoticeIdentityFact(label: context.label, value: name, icon: "building.2")
                }
            }
            if let edition = state.edition {
                NoticeIdentityFact(label: "회차", value: "제\(edition)회", icon: "calendar")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.quaternary.opacity(0.4), in: RoundedRectangle(cornerRadius: 16))
        .accessibilityIdentifier("identity.\(state.id)")
    }
}
