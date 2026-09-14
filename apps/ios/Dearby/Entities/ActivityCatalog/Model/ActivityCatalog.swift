import Foundation

struct ActivityCatalog: Decodable {
    let schemaVersion: String
    let mode: String
    let snapshotAt: String
    let sources: [ActivitySource]
    let organizations: [ActivityOrganization]
    let activities: [ActivityNotice]

    var feed: [ActivityNotice] {
        activities.filter(\.demoVisible).sorted {
            ($0.favoriteOrganizationId == nil ? 1 : 0) < ($1.favoriteOrganizationId == nil ? 1 : 0)
        }
    }

    func summary(for notice: ActivityNotice) -> ActivityNoticeSummary {
        ActivityNoticeSummary(notice: notice,
                              organization: organization(notice.favoriteOrganizationId),
                              contextNames: contextNames(for: notice))
    }

    func organization(_ id: String?) -> ActivityOrganization? {
        organizations.first { $0.id == id }
    }

    func organizationPath(_ id: String?) -> [ActivityOrganization] {
        var path: [ActivityOrganization] = []
        var seen: Set<String> = []
        var current = organization(id)
        while let item = current, seen.insert(item.id).inserted {
            path.insert(item, at: 0)
            current = organization(item.parentOrganizationId)
        }
        return path
    }

    func contextNames(for notice: ActivityNotice) -> String {
        var seen: Set<String> = []
        return notice.contexts.compactMap { context in
            guard seen.insert(context.organizationId).inserted else { return nil }
            return organization(context.organizationId)?.name
        }.joined(separator: " · ")
    }

    func sourceURL(for activity: ActivityNotice) -> URL? {
        sources.first { $0.id == activity.sourceIds.first }.flatMap { URL(string: $0.url) }
    }
}

struct ActivitySource: Decodable {
    let id: String
    let url: String
}

struct ActivityOrganization: Decodable, Identifiable {
    let id: String
    let name: String
    let parentOrganizationId: String?
}

struct ActivityField: Decodable {
    let summary: String
}

struct ActivityIssue: Decodable {
    let summary: String
}

struct ActivityContext: Decodable {
    let organizationId: String
    let role: String

    var label: String {
        switch role {
        case "venue_institution": "개최 기관"
        case "audience_institution": "참여 대상 기관"
        case "co_operator": "공동 운영"
        default: "행사 관련 기관"
        }
    }
}

struct ActivitySchedule: Decodable {
    let phase: String
    let startsOn: String?
    let startsAt: String?
    let endsAt: String?

    var summary: String {
        let label = ["event": "행사", "preliminary": "예선", "finalist_announcement": "결선 진출 발표", "final": "결선·시상"][phase] ?? phase
        // The contract records source-local ISO timestamps with an explicit offset.
        let start = startsAt.map { String($0.prefix(16)).replacingOccurrences(of: "T", with: " ") } ?? startsOn ?? "일정 미확인"
        let end = endsAt.map { " ~ " + String($0.prefix(16)).replacingOccurrences(of: "T", with: " ") } ?? ""
        return "\(label): \(start)\(end) (한국 시간)"
    }
}

struct ActivityNotice: Decodable, Identifiable {
    let id: String
    let title: String
    let summary: String
    let demoVisible: Bool
    let favoriteOrganizationId: String?
    let sourceIds: [String]
    let audience: ActivityField
    let eligibility: ActivityField
    let application: ActivityField
    let location: ActivityField
    let schedule: [ActivitySchedule]
    let benefits: [ActivityField]
    let qualityIssues: [ActivityIssue]
    let categoryPath: [String]
    let contexts: [ActivityContext]
    let edition: Int?

    var categorySummary: String {
        let labels = ["recruitment": "채용", "recruitment_event": "채용행사",
                      "competition": "대회", "career": "진로", "mentoring": "멘토링",
                      "academic_administration": "학사 행정"]
        return categoryPath.map { labels[$0] ?? $0 }.joined(separator: " › ")
    }
}
