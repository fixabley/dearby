import SwiftUI

struct NoticeDetailView: View {
    let notice: ActivityNotice
    let catalog: ActivityCatalog

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(notice.title).font(.title2.bold())
                Text(notice.summary)
                NoticeIdentityView(notice: notice, catalog: catalog)
                Divider()
                detail("참여 대상", notice.audience.summary)
                detail("참여 조건", notice.eligibility.summary)
                detail("신청 기간", notice.application.summary)
                ForEach(Array(notice.schedule.enumerated()), id: \.offset) { _, phase in
                    detail("활동 일정", phase.summary)
                }
                detail("활동 장소", notice.location.summary)
                ForEach(Array(notice.benefits.enumerated()), id: \.offset) { _, benefit in
                    detail("혜택", benefit.summary)
                }
                ForEach(Array(notice.qualityIssues.enumerated()), id: \.offset) { _, issue in
                    detail("확인 필요", issue.summary)
                }
                Text("원문을 검토해 만든 샘플입니다. 현재 모집 여부와 변경된 조건은 원문에서 확인해 주세요.")
                    .font(.footnote).foregroundStyle(.secondary)
                if let sourceURL = catalog.sourceURL(for: notice) {
                    Link("원문 공고 열기", destination: sourceURL)
                }
            }.frame(maxWidth: 620, alignment: .leading).padding(24)
        }
        .navigationTitle("공고 정보")
        .presentationDragIndicator(.visible)
    }

    private func detail(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value)
        }
    }
}

private struct NoticeIdentityView: View {
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
