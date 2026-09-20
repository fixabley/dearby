import Foundation

struct NoticeDetailState: Identifiable {
    let id: String
    let title: String
    let aiDescription: String
    let organizationID: String?
    let organizationName: String?
    let organizationPath: [String]
    let organizationLinks: [NoticeInstitutionState]
    let contexts: [NoticeInstitutionState]
    let categorySummary: String
    let targetUser: String
    let participationCondition: String
    let application: NoticeApplicationState
    let applicationURL: URL?
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
