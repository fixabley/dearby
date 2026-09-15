import Foundation

struct NoticeModel: Decodable, Identifiable {
    let id: String
    let title: String
    let aiDescription: String
    let demoVisible: Bool
    let favoriteOrganizationId: String?
    let sourceIds: [String]
    let targetUser: String
    let participationCondition: String
    let applicationInformation: NoticeApplication
    let location: NoticeLocation
    let schedule: [NoticeSchedule]
    let benefits: [String]
    let qualityIssues: [String]
    let categoryPath: [String]
    let contexts: [NoticeContext]
    let edition: Int?
    var organizationLinks: [NoticeContext] = []
    var evidence: [NoticeEvidence] = []

    var sources: [NoticeSource] = []
    var sourceURL: URL? { sources.first { $0.id == sourceIds.first }.flatMap { URL(string: $0.url) } }
    var descriptionProvenance: String { "reviewed_sample.summary" }
    var schedules: [NoticePhase] {
        schedule.map { phase in
            NoticePhase(period: phase, locations: phase.mode == "online" ? [] : location.venues.filter { $0.phase == phase.phase }, locationSummary: location.summary)
        }
    }

    var categorySummary: String {
        let labels = ["recruitment": "채용", "recruitment_event": "채용행사",
                      "competition": "대회", "career": "진로", "mentoring": "멘토링",
                      "academic_administration": "학사 행정"]
        return categoryPath.map { labels[$0] ?? $0 }.joined(separator: " › ")
    }
}

// Decode only display summaries from the structured source contract.
// An extension preserves Swift's memberwise initializer for fixtures and projections.
extension NoticeModel {
    private enum CodingKeys: String, CodingKey {
        case id, title, summary, demoVisible, favoriteOrganizationId, sourceIds
        case audience, eligibility, application, location, schedule, benefits, qualityIssues
        case categoryPath, contexts, edition, organizationLinks
    }
    private enum SummaryKey: String, CodingKey { case summary }

    init(from decoder: any Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(String.self, forKey: .id)
        title = try values.decode(String.self, forKey: .title)
        aiDescription = try values.decode(String.self, forKey: .summary)
        demoVisible = try values.decode(Bool.self, forKey: .demoVisible)
        favoriteOrganizationId = try values.decodeIfPresent(String.self, forKey: .favoriteOrganizationId)
        sourceIds = try values.decode([String].self, forKey: .sourceIds)
        targetUser = try values.nestedContainer(keyedBy: SummaryKey.self, forKey: .audience).decode(String.self, forKey: .summary)
        participationCondition = try values.nestedContainer(keyedBy: SummaryKey.self, forKey: .eligibility).decode(String.self, forKey: .summary)
        applicationInformation = try values.decode(NoticeApplication.self, forKey: .application)
        location = try values.decode(NoticeLocation.self, forKey: .location)
        schedule = try values.decode([NoticeSchedule].self, forKey: .schedule)
        var benefitValues = try values.nestedUnkeyedContainer(forKey: .benefits)
        var decodedBenefits: [String] = []
        while !benefitValues.isAtEnd {
            decodedBenefits.append(try benefitValues.nestedContainer(keyedBy: SummaryKey.self).decode(String.self, forKey: .summary))
        }
        benefits = decodedBenefits
        var issueValues = try values.nestedUnkeyedContainer(forKey: .qualityIssues)
        var decodedIssues: [String] = []
        while !issueValues.isAtEnd {
            decodedIssues.append(try issueValues.nestedContainer(keyedBy: SummaryKey.self).decode(String.self, forKey: .summary))
        }
        qualityIssues = decodedIssues
        categoryPath = try values.decode([String].self, forKey: .categoryPath)
        contexts = try values.decode([NoticeContext].self, forKey: .contexts)
        edition = try values.decodeIfPresent(Int.self, forKey: .edition)
        organizationLinks = try values.decodeIfPresent([NoticeContext].self, forKey: .organizationLinks) ?? []
        evidence = try NoticeEvidenceDecoder.collect(from: decoder)
    }
}
