import Foundation

/// `GET /v1/catalog`. Transport or decoding failure is an error, never an empty catalog.
enum CatalogClient {
    static func fetch(api: URL) async throws -> [ActivityModel] {
        var request = URLRequest(url: api.appending(path: "v1/catalog"), cachePolicy: .reloadIgnoringLocalCacheData,
                                 timeoutInterval: 15)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
        return try decode(data)
    }
    static func decode(_ data: Data) throws -> [ActivityModel] {
        let payload = try JSONDecoder().decode(Payload.self, from: data)
        let organizations = Dictionary(payload.organizations.map { ($0.id, $0.name) }, uniquingKeysWith: { first, _ in first })
        return payload.activities.map { activity in
            ActivityModel(id: activity.id, title: activity.title, summary: activity.summary,
                organization: organizations[activity.organizationId], participationType: activity.participationType,
                recruitmentStatus: activity.recruitmentStatus, isRecruiting: activity.isRecruiting, freshness: activity.freshness,
                recruitmentStartAt: activity.recruitmentStartAt, recruitmentEndAt: activity.recruitmentEndAt,
                sourceCheckedAt: activity.sourceCheckedAt, validUntil: activity.validUntil, dateLabel: activity.dateLabel,
                location: activity.location, cost: activity.cost, audience: activity.audience, roles: activity.roles,
                schedules: activity.schedules.compactMap { schedule in
                    guard let start = ActivityModel.instant(schedule.startAt), let end = ActivityModel.instant(schedule.endAt) else { return nil }
                    return ActivityScheduleModel(id: schedule.id, title: schedule.title, start: start, end: end,
                                                 dateLabel: schedule.dateLabel, timeZone: schedule.timeZone)
                },
                officialUrl: activity.officialUrl, applicationUrl: activity.applicationUrl, sourceNote: activity.sourceNote)
        }
    }
    private struct Payload: Decodable {
        let organizations: [Organization]
        let activities: [Activity]
    }
    private struct Organization: Decodable { let id: String; let name: String }
    private struct Schedule: Decodable {
        let id: String; let title: String; let startAt: String?; let endAt: String?; let dateLabel: String; let timeZone: String
    }
    private struct Activity: Decodable {
        let id: String; let organizationId: String; let title: String; let summary: String
        let participationType: ActivityModel.Participation; let recruitmentStatus: String; let isRecruiting: Bool
        let recruitmentStartAt: String?; let recruitmentEndAt: String?; let dateLabel: String
        let location: String?; let cost: String?; let audience: String?; let roles: [String]; let schedules: [Schedule]
        let officialUrl: String; let applicationUrl: String?; let sourceCheckedAt: String?; let validUntil: String?
        let freshness: String; let sourceNote: String
    }
}
