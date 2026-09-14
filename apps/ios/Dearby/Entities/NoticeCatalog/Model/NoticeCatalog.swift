import Foundation

struct NoticeCatalog: Decodable {
    let schemaVersion: String
    let mode: String
    let snapshotAt: String
    let sources: [NoticeSource]
    let organizations: [NoticeOrganization]
    let notices: [Notice]

    private enum CodingKeys: String, CodingKey {
        case schemaVersion, mode, snapshotAt, sources, organizations
        case notices = "activities" // Existing wire key; app domain is Notice.
    }

    var feed: [Notice] {
        notices.filter(\.demoVisible).sorted {
            ($0.favoriteOrganizationId == nil ? 1 : 0) < ($1.favoriteOrganizationId == nil ? 1 : 0)
        }
    }

    func summary(for notice: Notice) -> NoticeSummary {
        NoticeSummary(notice: notice,
                              organization: organization(notice.favoriteOrganizationId),
                              contextNames: contextNames(for: notice))
    }

    func organization(_ id: String?) -> NoticeOrganization? {
        organizations.first { $0.id == id }
    }

    func organizationPath(_ id: String?) -> [NoticeOrganization] {
        var path: [NoticeOrganization] = []
        var seen: Set<String> = []
        var current = organization(id)
        while let item = current, seen.insert(item.id).inserted {
            path.insert(item, at: 0)
            current = organization(item.parentOrganizationId)
        }
        return path
    }

    func contextNames(for notice: Notice) -> String {
        var seen: Set<String> = []
        return notice.contexts.compactMap { context in
            guard seen.insert(context.organizationId).inserted else { return nil }
            return organization(context.organizationId)?.name
        }.joined(separator: " · ")
    }

    func sourceURL(for notice: Notice) -> URL? {
        sources.first { $0.id == notice.sourceIds.first }.flatMap { URL(string: $0.url) }
    }
}

struct NoticeSource: Decodable {
    let id: String
    let url: String
    var kind: String? = nil
    var checkedAt: String? = nil
    var access: String? = nil
    var note: String? = nil
}

struct NoticeOrganization: Decodable, Identifiable {
    let id: String
    let name: String
    let parentOrganizationId: String?
}

struct NoticeContext: Decodable {
    let organizationId: String
    let role: String
    var basis: String? = nil
    var note: String? = nil

    var label: String {
        switch role {
        case "venue_institution": "개최 기관"
        case "audience_institution": "참여 대상 기관"
        case "co_operator": "공동 운영"
        default: "행사 관련 기관"
        }
    }
}

struct Notice: Decodable, Identifiable {
    let id: String
    let title: String
    let summary: String
    let demoVisible: Bool
    let favoriteOrganizationId: String?
    let sourceIds: [String]
    let audience: String
    let eligibility: String
    let application: NoticeApplication
    let location: NoticeLocation
    let schedule: [NoticeSchedule]
    let benefits: [String]
    let qualityIssues: [String]
    let categoryPath: [String]
    let contexts: [NoticeContext]
    let edition: Int?
    var organizationLinks: [NoticeContext] = []
    var evidence: [NoticeEvidence] = []

    var categorySummary: String {
        let labels = ["recruitment": "채용", "recruitment_event": "채용행사",
                      "competition": "대회", "career": "진로", "mentoring": "멘토링",
                      "academic_administration": "학사 행정"]
        return categoryPath.map { labels[$0] ?? $0 }.joined(separator: " › ")
    }
}

// Decode only display summaries from the structured source contract.
// An extension preserves Swift's memberwise initializer for fixtures and projections.
extension Notice {
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
        summary = try values.decode(String.self, forKey: .summary)
        demoVisible = try values.decode(Bool.self, forKey: .demoVisible)
        favoriteOrganizationId = try values.decodeIfPresent(String.self, forKey: .favoriteOrganizationId)
        sourceIds = try values.decode([String].self, forKey: .sourceIds)
        audience = try values.nestedContainer(keyedBy: SummaryKey.self, forKey: .audience).decode(String.self, forKey: .summary)
        eligibility = try values.nestedContainer(keyedBy: SummaryKey.self, forKey: .eligibility).decode(String.self, forKey: .summary)
        application = try values.decode(NoticeApplication.self, forKey: .application)
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
