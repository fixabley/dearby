import Foundation

/// Transient read projection, deliberately not Codable/persisted. Organization references remain IDs.
struct ActivityDetail: Identifiable {
    let id: String
    let title: String
    let aiDescription: String
    let descriptionProvenance: String
    let organizationID: String?
    let organizationPath: [ActivityOrganization]
    let organizationLinks: [ActivityDetailContext]
    let categoryPath: [String]
    let categorySummary: String
    let contexts: [ActivityDetailContext]
    let targetUser: String
    let participationCondition: String
    let applicationInformation: ActivityApplication
    let schedules: [ActivityDetailSchedule]
    let location: ActivityLocation
    let benefits: [String]
    let qualityIssues: [String]
    let edition: Int?
    let sourceURL: URL?
    let sources: [ActivitySource]
    let evidence: [ActivityEvidence]
}

struct ActivityDetailContext {
    let reference: ActivityContext
    let organizationName: String?
}

struct ActivityDetailSchedule {
    let period: ActivitySchedule
    let locations: [ActivityVenue]
    let locationSummary: String
}
