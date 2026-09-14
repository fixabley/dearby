import Foundation

/// Transient read projection, deliberately not Codable/persisted. Organization references remain IDs.
struct NoticeDetail: Identifiable {
    let id: String
    let title: String
    let aiDescription: String
    let descriptionProvenance: String
    let organizationID: String?
    let organizationPath: [NoticeOrganization]
    let organizationLinks: [NoticeDetailContext]
    let categoryPath: [String]
    let categorySummary: String
    let contexts: [NoticeDetailContext]
    let targetUser: String
    let participationCondition: String
    let applicationInformation: NoticeApplication
    let schedules: [NoticeDetailSchedule]
    let location: NoticeLocation
    let benefits: [String]
    let qualityIssues: [String]
    let edition: Int?
    let sourceURL: URL?
    let sources: [NoticeSource]
    let evidence: [NoticeEvidence]

    /// Pure construction from already fetched values; no repository, cache or IO.
    init(notice: Notice, organizationPath: [NoticeOrganization],
         contexts: [NoticeDetailContext], organizationLinks: [NoticeDetailContext],
         sources: [NoticeSource]) {
        self.id = notice.id
        self.title = notice.title
        self.aiDescription = notice.summary
        self.descriptionProvenance = "reviewed_sample.summary"
        self.organizationID = notice.favoriteOrganizationId
        self.organizationPath = organizationPath
        self.organizationLinks = organizationLinks
        self.categoryPath = notice.categoryPath
        self.categorySummary = notice.categorySummary
        self.contexts = contexts
        self.targetUser = notice.audience
        self.participationCondition = notice.eligibility
        self.applicationInformation = notice.application
        self.location = notice.location
        self.benefits = notice.benefits
        self.qualityIssues = notice.qualityIssues
        self.edition = notice.edition
        self.sources = sources
        sourceURL = sources.first { $0.id == notice.sourceIds.first }.flatMap { URL(string: $0.url) }
        evidence = notice.evidence.map { reference in
            var resolved = reference
            resolved.sourceURL = sources.first { $0.id == reference.sourceId }.flatMap { URL(string: $0.url) }
            return resolved
        }
        schedules = notice.schedule.map { phase in
            NoticeDetailSchedule(period: phase,
                locations: phase.mode == "online" ? [] : notice.location.venues.filter { $0.phase == phase.phase },
                locationSummary: notice.location.summary)
        }
    }

}

struct NoticeDetailContext {
    let reference: NoticeContext
    let organizationName: String?
}

struct NoticeDetailSchedule {
    let period: NoticeSchedule
    let locations: [NoticeVenue]
    let locationSummary: String
}
