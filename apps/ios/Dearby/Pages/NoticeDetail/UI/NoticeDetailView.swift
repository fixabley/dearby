import SwiftUI

struct NoticeDetailView: View {
    let notice: ActivityNotice
    let catalog: ActivityCatalog
    let onAddApplication: (() -> Void)?
    let onOpenMap: (ActivityVenue) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(notice.title).font(.title2.bold())
                Text(notice.summary)
                NoticeIdentityView(notice: notice, catalog: catalog)
                Divider()
                NoticeDetailField(title: "참여 대상", value: notice.audience)
                NoticeDetailField(title: "참여 조건", value: notice.eligibility)
                NoticeApplicationView(summary: notice.application.summary, onAddToCalendar: onAddApplication)
                ForEach(Array(notice.schedule.enumerated()), id: \.offset) { _, phase in
                    NoticeDetailField(title: "활동 일정", value: phase.summary)
                }
                NoticeLocationView(location: notice.location, onOpenMap: onOpenMap)
                ForEach(Array(notice.benefits.enumerated()), id: \.offset) { _, benefit in
                    NoticeDetailField(title: "혜택", value: benefit)
                }
                ForEach(Array(notice.qualityIssues.enumerated()), id: \.offset) { _, issue in
                    NoticeDetailField(title: "확인 필요", value: issue)
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
}
