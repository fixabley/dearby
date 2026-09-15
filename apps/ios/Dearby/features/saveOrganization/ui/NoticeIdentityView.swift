import SwiftUI

/// Identity panel owned by the NoticeDetail slice.
struct NoticeIdentityView: View {
    let state: NoticeIdentityState

    var body: some View {
        Group {
            if let target = state.organizationName {
                InformationRow(title: "관심 조직", value: target, systemImage: "heart")
                let ancestors = state.organizationPath
                if !ancestors.isEmpty {
                    InformationRow(title: "상위 조직", value: ancestors.joined(separator: " › "), systemImage: "building.2")
                }
                Text("저장되는 관심 조직: \(target)")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            InformationRow(title: "활동 분류", value: state.categorySummary, systemImage: "square.grid.2x2")
            ForEach(Array(state.contexts.enumerated()), id: \.offset) { _, context in
                if let name = context.organizationName {
                    InformationRow(title: context.label, value: name, systemImage: "building.2")
                }
            }
            if let edition = state.edition {
                InformationRow(title: "회차", value: "제\(edition)회", systemImage: "calendar")
            }
        }
    }
}
