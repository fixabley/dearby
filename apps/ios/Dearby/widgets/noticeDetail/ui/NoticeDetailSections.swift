import SwiftUI

struct NoticeDetailSections: View {
    let state: NoticeDetailState
    let onAddSchedule: [(() -> Void)?]
    let onAddApplication: (() -> Void)?
    let onOpenMap: (NoticeVenue) -> Void

    let busyCalendar: BusyCalendarSession

    var body: some View {
        Group {
            Section {
                Text(state.title).font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)
                Text(state.aiDescription)
            }
            Section("조직과 분류") {
                NoticeIdentityView(state: NoticeIdentityState(organizationName: state.organizationName,
                    organizationPath: state.organizationPath, categorySummary: state.categorySummary,
                    contexts: state.contexts, edition: state.edition))
                    .accessibilityIdentifier("identity.\(state.id)")
            }
            Section("참여 안내") {
                NoticeParticipationView(audience: state.targetUser, condition: state.participationCondition)
            }
            Section("신청") {
                NoticeApplicationView(state: state.application, onAddToCalendar: onAddApplication)

            }
            Section("활동") {
                ForEach(Array(state.schedules.enumerated()), id: \.offset) { index, phase in
                    NoticeScheduleView(state: phase,
                                       onAddToCalendar: onAddSchedule.indices.contains(index) ? onAddSchedule[index] : nil,
                                       busyCalendar: busyCalendar, index: index,
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
            NoticeSourceSection(qualityIssues: state.qualityIssues, sourceURL: state.sourceURL)
        }
    }
}
