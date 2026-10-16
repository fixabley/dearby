import Foundation

/// `GET /v1/activities`. Transport or decoding failure is an error, never an empty page.
enum ActivityClient {
    static func fetch(api: URL, query: String, cursor: Int64?) async throws -> ActivityPage {
        var components = URLComponents(url: api.appending(path: "v1/activities"), resolvingAgainstBaseURL: false)
        var items: [URLQueryItem] = []
        if !query.isEmpty { items.append(URLQueryItem(name: "q", value: query)) }
        if let cursor { items.append(URLQueryItem(name: "cursor", value: String(cursor))) }
        components?.queryItems = items.isEmpty ? nil : items
        guard let url = components?.url else { throw URLError(.badURL) }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(acceptLanguage(Locale.preferredLanguages), forHTTPHeaderField: "Accept-Language")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
        return try decode(data)
    }
    /// The device language order, so the API answers activity text in English when English comes before Korean.
    /// URLSession's own header follows the app's localizations (Korean only), not the device.
    static func acceptLanguage(_ languages: [String]) -> String {
        let tags = languages.prefix(5)
        guard !tags.isEmpty else { return "ko" }
        return tags.enumerated().map { index, tag in index == 0 ? tag : "\(tag);q=\(String(format: "%.1f", 1 - Double(index) * 0.1))" }
            .joined(separator: ", ")
    }
    static func decode(_ data: Data) throws -> ActivityPage {
        let payload = try JSONDecoder().decode(Payload.self, from: data)
        let items = payload.items.map { activity in
            ActivityModel(id: String(activity.id), title: activity.name, summary: activity.description,
                organization: activity.organization.name,
                recruitmentStartAt: activity.application?.startAt, recruitmentEndAt: activity.application?.endAt,
                dateLabel: activity.activitySchedule?.dateLabel ?? "",
                schedules: activity.schedules.compactMap { schedule in
                    guard let start = ActivityModel.instant(schedule.startAt), let end = ActivityModel.instant(schedule.endAt) else { return nil }
                    // The calendar overlap screen needs a known zone.
                    let zone = schedule.timeZone.flatMap { TimeZone(identifier: $0) == nil ? nil : $0 } ?? "Asia/Seoul"
                    return ActivityScheduleModel(id: String(schedule.id), title: schedule.title, start: start, end: end,
                                                 dateLabel: schedule.dateLabel, timeZone: zone)
                },
                officialUrl: activity.officialUrl, imageUrl: activity.imageUrl)
        }
        return ActivityPage(items: items, nextCursor: payload.nextCursor)
    }
    private struct Payload: Decodable {
        let items: [Activity]
        let nextCursor: Int64?
    }
    private struct Named: Decodable { let name: String }
    private struct Schedule: Decodable {
        let id: Int64; let title: String; let startAt: String?; let endAt: String?; let timeZone: String?; let dateLabel: String
    }
    private struct Activity: Decodable {
        let id: Int64; let name: String; let description: String; let organization: Named
        let officialUrl: String; let imageUrl: String?
        let application: Schedule?; let activitySchedule: Schedule?; let schedules: [Schedule]
    }
}
