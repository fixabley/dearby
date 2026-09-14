import SwiftUI

struct NoticeDetailView: View {
    let state: NoticeDetailState
    let onAddSchedule: [(() -> Void)?]
    let onAddApplication: (() -> Void)?
    let onOpenMap: (NoticeVenue) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(state.title).font(.title2.bold())
                Text(state.aiDescription)
                NoticeIdentityView(state: state)
                Divider()
                NoticeDetailField(title: "참여 대상", value: state.targetUser)
                NoticeDetailField(title: "참여 조건", value: state.participationCondition)
                NoticeApplicationView(summary: state.applicationSummary, onAddToCalendar: onAddApplication)
                ForEach(Array(state.scheduleSummaries.enumerated()), id: \.offset) { index, phase in
                    NoticeScheduleView(summary: phase,
                                       onAddToCalendar: onAddSchedule.indices.contains(index) ? onAddSchedule[index] : nil)
                }
                NoticeLocationView(location: state.location, onOpenMap: onOpenMap)
                ForEach(Array(state.benefits.enumerated()), id: \.offset) { _, benefit in
                    NoticeDetailField(title: "혜택", value: benefit)
                }
                ForEach(Array(state.qualityIssues.enumerated()), id: \.offset) { _, issue in
                    NoticeDetailField(title: "확인 필요", value: issue)
                }
                Text("원문을 검토해 만든 샘플입니다. 현재 모집 여부와 변경된 조건은 원문에서 확인해 주세요.")
                    .font(.footnote).foregroundStyle(.secondary)
                if let sourceURL = state.sourceURL {
                    Link("원문 공고 열기", destination: sourceURL)
                }
            }.frame(maxWidth: 620, alignment: .leading).padding(24)
        }
        .navigationTitle("공고 정보")
        .presentationDragIndicator(.visible)
    }
}
