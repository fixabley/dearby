import SwiftUI

struct NoticeDetailView: View {
    let state: NoticeDetailState
    let onAddSchedule: [(() -> Void)?]
    let onAddApplication: (() -> Void)?
    let onOpenMap: (NoticeVenue) -> Void

    var body: some View {
        List {
            Section {
                Text(state.title).font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)
                Text(state.aiDescription)
            }
            Section("조직과 분류") {
                NoticeIdentityView(state: state)
            }
            Section("참여 안내") {
                InformationRow(title: "참여 대상", value: state.targetUser)
                InformationRow(title: "참여 조건", value: state.participationCondition)
            }
            Section("신청") {
                NoticeApplicationView(time: state.applicationTime, original: state.applicationSummary, onAddToCalendar: onAddApplication)

            }
            Section("활동") {
                ForEach(Array(state.schedules.enumerated()), id: \.offset) { index, phase in
                    NoticeScheduleView(state: phase,
                                       onAddToCalendar: onAddSchedule.indices.contains(index) ? onAddSchedule[index] : nil,
                                       onOpenMap: onOpenMap)
                }
                DisclosureGroup("원문 장소 안내") {
                    Text(state.location.summary).font(.footnote).foregroundStyle(.secondary)
                }
                if state.schedules.isEmpty {
                    NoticeLocationView(location: state.location, onOpenMap: onOpenMap)
                }
            }
            if !state.benefits.isEmpty {
                Section("혜택") {
                    ForEach(Array(state.benefits.enumerated()), id: \.offset) { _, benefit in
                        Text(benefit)
                    }
                }
            }
            Section {
                ForEach(Array(state.qualityIssues.enumerated()), id: \.offset) { _, issue in
                    StatusMessage(text: issue)
                }
                if let sourceURL = state.sourceURL {
                    Link(destination: sourceURL) {
                        Label("원문 공고 열기", systemImage: "arrow.up.right.square")
                            .labelStyle(.iconOnly)
                            .frame(minWidth: 44, minHeight: 44, alignment: .leading)
                    }
                }
            } header: {
                Text("출처와 확인 사항")
            } footer: {
                Text("원문을 검토해 만든 샘플입니다. 현재 모집 여부와 변경된 조건은 원문에서 확인해 주세요.")
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("공고 정보")
        .presentationDragIndicator(.visible)
    }
}
