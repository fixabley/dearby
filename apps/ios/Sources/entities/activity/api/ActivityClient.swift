import Foundation

/// `GET /v1/activities` and `GET /v1/discovery/programs`. Transport or decoding failure is an error, never an empty page.
enum ActivityClient {
    /// Activities, optionally those of one program (`programId`); `size` up to 100 so an expanded program loads in one page.
    static func fetch(api: URL, query: String, cursor: Int64?, programID: Int64? = nil, size: Int? = nil) async throws -> ActivityPage {
        var items: [URLQueryItem] = []
        if !query.isEmpty { items.append(URLQueryItem(name: "q", value: query)) }
        if let programID { items.append(URLQueryItem(name: "programId", value: String(programID))) }
        if let cursor { items.append(URLQueryItem(name: "cursor", value: String(cursor))) }
        if let size { items.append(URLQueryItem(name: "size", value: String(size))) }
        return try decode(try await get(api, path: "v1/activities", items))
    }
    /// Programs with open activities (contract "발견 프로그램 묶음"), matching `query` by program, organization or activity name.
    static func fetchPrograms(api: URL, query: String, cursor: Int64?) async throws -> ProgramPage {
        var items: [URLQueryItem] = []
        if !query.isEmpty { items.append(URLQueryItem(name: "q", value: query)) }
        if let cursor { items.append(URLQueryItem(name: "cursor", value: String(cursor))) }
        let payload = try JSONDecoder().decode(ProgramPayload.self, from: try await get(api, path: "v1/discovery/programs", items))
        return ProgramPage(items: payload.items.map { program in
            ProgramSummary(id: program.id, name: program.name, nameEn: program.nameEn, organization: program.organization.name,
                           openActivityCount: program.openActivityCount, nearestRecruitmentEndAt: program.nearestRecruitmentEndAt,
                           matchedActivityIDs: program.matchedActivityIds.map(String.init))
        }, nextCursor: payload.nextCursor)
    }
    private static func get(_ api: URL, path: String, _ items: [URLQueryItem]) async throws -> Data {
        var components = URLComponents(url: api.appending(path: path), resolvingAgainstBaseURL: false)
        components?.queryItems = items.isEmpty ? nil : items
        guard let url = components?.url else { throw URLError(.badURL) }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(acceptLanguage(Locale.preferredLanguages), forHTTPHeaderField: "Accept-Language")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
        return data
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
                organization: activity.organization.name, program: activity.program?.name,
                recruitmentStartAt: activity.application?.startAt, recruitmentEndAt: activity.application?.endAt,
                dateLabel: activity.activitySchedule?.dateLabel ?? "",
                schedules: activity.schedules.compactMap { schedule in
                    guard let start = ActivityModel.instant(schedule.startAt), let end = ActivityModel.instant(schedule.endAt) else { return nil }
                    // The calendar overlap screen needs a known zone.
                    let zone = schedule.timeZone.flatMap { TimeZone(identifier: $0) == nil ? nil : $0 } ?? "Asia/Seoul"
                    return ActivityScheduleModel(id: String(schedule.id), title: schedule.title, start: start, end: end,
                                                 dateLabel: schedule.dateLabel, timeZone: zone)
                },
                hasApplication: activity.application != nil,
                stages: activity.schedules.map(stage), activityStage: activity.activitySchedule.map(stage),
                officialUrl: activity.officialUrl, imageUrl: activity.imageUrl,
                eligibility: activity.eligibility?.value.flatMap { $0.eligibility(depth: 0) },
                category: activity.category.flatMap(ActivityCategory.init(rawValue:)))
        }
        return ActivityPage(items: items, nextCursor: payload.nextCursor)
    }
    /// Stages keep every schedule: timed (either bound may be missing), all-day, or date-less. An unknown kind reads as an event.
    private static func stage(_ schedule: Schedule) -> ActivityStageModel {
        let zone = schedule.timeZone.flatMap { TimeZone(identifier: $0) == nil ? nil : $0 } ?? "Asia/Seoul"
        let kind = schedule.kind.flatMap(ActivityStageModel.Kind.init(rawValue:)) ?? .event
        let startAt = ActivityModel.instant(schedule.startAt), endAt = ActivityModel.instant(schedule.endAt)
        if startAt != nil || endAt != nil {
            return ActivityStageModel(id: String(schedule.id), title: schedule.title, kind: kind, start: startAt ?? endAt, end: endAt ?? startAt,
                                      allDay: false, dateLabel: schedule.dateLabel, timeZone: zone)
        }
        let startDate = seoulDay(schedule.startDate), endDate = seoulDay(schedule.endDateExclusive)
        return ActivityStageModel(id: String(schedule.id), title: schedule.title, kind: kind, start: startDate, end: endDate ?? startDate,
                                  allDay: startDate != nil, dateLabel: schedule.dateLabel, timeZone: "Asia/Seoul")
    }
    /// `yyyy-MM-dd` as Seoul midnight; nil when absent or unreadable.
    private static func seoulDay(_ raw: String?) -> Date? {
        guard let raw else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: raw)
    }
    private struct Payload: Decodable {
        let items: [Activity]
        let nextCursor: Int64?
    }
    private struct ProgramPayload: Decodable {
        let items: [Program]
        let nextCursor: Int64?
    }
    private struct Program: Decodable {
        let id: Int64; let name: String; let nameEn: String?; let organization: Named
        let openActivityCount: Int; let nearestRecruitmentEndAt: String?; let matchedActivityIds: [Int64]
    }
    private struct Named: Decodable { let name: String }
    private struct Schedule: Decodable {
        let id: Int64; let title: String; let kind: String?; let startAt: String?; let endAt: String?; let timeZone: String?
        let startDate: String?; let endDateExclusive: String?; let dateLabel: String
    }
    private struct Activity: Decodable {
        let id: Int64; let name: String; let description: String; let organization: Named; let program: Named?
        let officialUrl: String; let imageUrl: String?
        let application: Schedule?; let activitySchedule: Schedule?; let schedules: [Schedule]
        /// Unreadable eligibility JSON must not fail the whole activity.
        let eligibility: Lenient<EligibilityNode>?
        /// 활동 종류 code; absent on older responses.
        let category: String?
    }
    /// Decodes to nil instead of throwing when the value has an unexpected shape.
    private struct Lenient<Value: Decodable>: Decodable {
        let value: Value?
        init(from decoder: Decoder) { value = try? Value(from: decoder) }
    }
    /// One eligibility group or condition as sent (camelCase keys, contract "대상자 조건"). Groups carry `op`/`items`.
    private struct EligibilityNode: Decodable {
        let op: String?; let items: [EligibilityNode]?
        let field: String?; let fieldEn: String?; let from: Double?; let to: Double?
        let values: [String]?; let valuesEn: [String]?; let note: String?; let noteEn: String?
        /// The top level must be a group; nesting stops at depth 3 (contract). Malformed nodes are dropped.
        func eligibility(depth: Int) -> Eligibility? {
            guard depth < 3, let op = op.flatMap(Eligibility.Op.init(rawValue:)), let items else { return nil }
            return Eligibility(op: op, items: items.compactMap { $0.item(depth: depth + 1) })
        }
        func item(depth: Int) -> Eligibility.Item? {
            if op != nil { return eligibility(depth: depth).map(Eligibility.Item.group) }
            guard let field, !field.isEmpty else { return nil }
            return .condition(Eligibility.Condition(field: field, fieldEn: fieldEn ?? "", from: from, to: to,
                                                    values: values ?? [], valuesEn: valuesEn ?? [], note: note))
        }
    }
}
