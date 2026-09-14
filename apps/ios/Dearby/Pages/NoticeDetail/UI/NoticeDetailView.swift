import SwiftUI

struct NoticeDetailView: View {
    let detail: NoticeDetail
    let onAddSchedule: [(() -> Void)?]
    let onAddApplication: (() -> Void)?
    let onOpenMap: (NoticeVenue) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(detail.title).font(.title2.bold())
                Text(detail.aiDescription)
                NoticeIdentityView(detail: detail)
                Divider()
                NoticeDetailField(title: "참여 대상", value: detail.targetUser)
                NoticeDetailField(title: "참여 조건", value: detail.participationCondition)
                NoticeApplicationView(summary: detail.applicationInformation.summary, onAddToCalendar: onAddApplication)
                ForEach(Array(detail.schedules.enumerated()), id: \.offset) { index, phase in
                    NoticeScheduleView(summary: phase.period.summary,
                                       onAddToCalendar: onAddSchedule.indices.contains(index) ? onAddSchedule[index] : nil)
                }
                NoticeLocationView(location: detail.location, onOpenMap: onOpenMap)
                ForEach(Array(detail.benefits.enumerated()), id: \.offset) { _, benefit in
                    NoticeDetailField(title: "혜택", value: benefit)
                }
                ForEach(Array(detail.qualityIssues.enumerated()), id: \.offset) { _, issue in
                    NoticeDetailField(title: "확인 필요", value: issue)
                }
                Text("원문을 검토해 만든 샘플입니다. 현재 모집 여부와 변경된 조건은 원문에서 확인해 주세요.")
                    .font(.footnote).foregroundStyle(.secondary)
                if let sourceURL = detail.sourceURL {
                    Link("원문 공고 열기", destination: sourceURL)
                }
            }.frame(maxWidth: 620, alignment: .leading).padding(24)
        }
        .navigationTitle("공고 정보")
        .presentationDragIndicator(.visible)
    }
}
