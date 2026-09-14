import Foundation

struct NoticeDetailState: Identifiable {
    let id: String
    let title: String
    let aiDescription: String
    let descriptionProvenance: String
    let organizationID: String?
    let organizationName: String?
    let organizationPath: [String]
    let organizationLinks: [NoticeInstitutionState]
    let contexts: [NoticeInstitutionState]
    let categorySummary: String
    let targetUser: String
    let participationCondition: String
    let applicationSummary: String
    let scheduleSummaries: [String]
    let applicationPeriod: String
    let schedules: [NoticeScheduleState]
    let location: NoticeLocation
    let benefits: [String]
    let qualityIssues: [String]
    let edition: Int?
    let sourceURL: URL?
    let sources: [NoticeSource]
    let evidence: [NoticeEvidence]
    var saved: Bool
}
struct NoticeInstitutionState {
    let organizationID: String
    let role: String
    let label: String
    let basis: String?
    let note: String?
    let organizationName: String?
}

/// Ordered presentation projection: index remains aligned with the App-owned calendar callbacks.
struct NoticeScheduleState {
    let title: String
    let period: String
    let originalSummary: String
    let location: String
    let venues: [NoticeVenue]

    init(phase: NoticePhase) {
        title = phase.period.mode == "online" && !phase.period.label.contains("온라인") ? "온라인 \(phase.period.label)" : phase.period.label
        period = CompactPeriod.period(start: phase.period.startsAt ?? phase.period.startsOn,
            end: phase.period.endsAt ?? phase.period.endsOn, timezone: phase.period.timezone, fallback: "일정 미확인")
        originalSummary = phase.period.summary
        location = phase.period.mode == "online" ? "온라인" :
            (phase.locations.isEmpty ? "장소 미확인" : phase.locations.map(\.name).joined(separator: " · "))
        venues = phase.locations.filter { $0.coordinates != nil }
    }
}
