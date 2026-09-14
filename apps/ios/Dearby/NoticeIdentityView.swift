import SwiftUI

struct NoticeIdentityView: View {
    let notice: ActivityNotice
    let catalog: ActivityCatalog

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if let target = catalog.organization(notice.favoriteOrganizationId) {
                fact("관심 조직", target.name, icon: "heart")
                let ancestors = catalog.organizationPath(target.id).dropLast()
                if !ancestors.isEmpty {
                    fact("상위 조직", ancestors.map(\.name).joined(separator: " › "), icon: "building.2")
                }
                Text("이 공고에서 관심 표시하면 \(target.name)이 저장돼요.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            fact("활동 분류", notice.categorySummary, icon: "square.grid.2x2")
            ForEach(Array(notice.contexts.enumerated()), id: \.offset) { _, context in
                if let organization = catalog.organization(context.organizationId) {
                    fact(context.label, organization.name, icon: "building.2")
                }
            }
            if let edition = notice.edition {
                fact("회차", "제\(edition)회", icon: "calendar")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.quaternary.opacity(0.4), in: RoundedRectangle(cornerRadius: 16))
        .accessibilityIdentifier("identity.\(notice.id)")
    }

    private func fact(_ label: String, _ value: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Label(label, systemImage: icon).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.subheadline.weight(.medium))
        }
    }
}
