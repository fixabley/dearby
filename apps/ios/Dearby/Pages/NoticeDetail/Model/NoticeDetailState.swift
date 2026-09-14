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
