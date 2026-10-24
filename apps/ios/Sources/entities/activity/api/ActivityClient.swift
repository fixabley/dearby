import Foundation

private enum ActivityFetchError: Error { case notFound }

/// `GET /v1/activities` and `GET /v1/discovery/programs`. Transport or decoding failure is an error, never an empty page.
enum ActivityClient {
    /// Activities, optionally those of one program (`programId`); `size` up to 100 so an expanded program loads in one page.
    /// `open` asks the server for currently open activities only (contract "열린 활동 판정 일원화"): the client never re-judges them.
    /// Tags are a search condition only: `q` matching a tag name is the server's rule, and `tagIds` is never sent (2026-10-19 밤).
    static func fetch(api: URL, query: String, cursor: Int64?, programID: Int64? = nil, size: Int? = nil, open: Bool = false) async throws -> ActivityPage {
        var items: [URLQueryItem] = []
        if !query.isEmpty { items.append(URLQueryItem(name: "q", value: query)) }
        if let programID { items.append(URLQueryItem(name: "programId", value: String(programID))) }
        if open { items.append(URLQueryItem(name: "open", value: "true")) }
        if let cursor { items.append(URLQueryItem(name: "cursor", value: String(cursor))) }
        if let size { items.append(URLQueryItem(name: "size", value: String(size))) }
        return try decode(try await send(api, path: "v1/activities", items))
    }
    /// One activity (`GET /v1/activities/:id`); nil on 404 (deleted, unpublished or without its program), so 내 활동 can show it as
    /// "삭제된 활동". Any other failure throws.
    static func fetch(api: URL, id: String) async throws -> ActivityModel? {
        do {
            return model(try JSONDecoder().decode(Activity.self, from: try await send(api, path: "v1/activities/\(id)", [])))
        } catch ActivityFetchError.notFound { return nil }
    }
    /// Programs for the discovery accordion (contract "발견 프로그램 묶음"), matching `query` by program, organization or activity name.
    /// `openOnly` false (contract "모집 중인 프로그램만 보기") also returns programs that had an activity in the last two years.
    /// `logoUrl` is an API-origin path (`/v1/programs/{id}/logo?v=…`, contract "프로그램 로고"), resolved here against `api`.
    static func fetchPrograms(api: URL, query: String, cursor: Int64?, openOnly: Bool = true) async throws -> ProgramPage {
        var items: [URLQueryItem] = []
        if !query.isEmpty { items.append(URLQueryItem(name: "q", value: query)) }
        if !openOnly { items.append(URLQueryItem(name: "openOnly", value: "false")) }
        if let cursor { items.append(URLQueryItem(name: "cursor", value: String(cursor))) }
        let payload = try JSONDecoder().decode(ProgramPayload.self, from: try await send(api, path: "v1/discovery/programs", items))
        return ProgramPage(items: payload.items.map { program in
            ProgramSummary(id: program.id, name: program.name, nameEn: program.nameEn, organization: program.organization.name,
                           openActivityCount: program.openActivityCount, nearestRecruitmentEndAt: program.nearestRecruitmentEndAt,
                           matchedActivityIDs: program.matchedActivityIds.map(String.init), latestActivityAt: program.latestActivityAt,
                           upcomingActivityCount: program.upcomingActivityCount ?? 0, nearestRecruitmentStartAt: program.nearestRecruitmentStartAt,
                           fieldTags: Array((program.fieldTags ?? []).prefix(2)).map(\.tag),
                           traitTags: (program.traitTags ?? program.tags ?? []).map(\.tag),
                           logoURL: program.logoUrl.flatMap { URL(string: $0, relativeTo: api)?.absoluteURL })
        }, nextCursor: payload.nextCursor)
    }
    /// 추천 (contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24): `GET /v1/discovery/recommendations`. Signed in, `bearer` is the access
    /// token and the server uses the saved preferences and 지원 자격 정보; signed out, the device preferences go in the query.
    /// Nil on 404 (a server without recommendations yet): the caller reads it as none. A 401 throws `RecommendationFailure.unauthorized`.
    static func fetchRecommendations(api: URL, size: Int, fieldTagIDs: [Int64], categories: [String], traitTagIDs: [Int64],
                                     bearer: String?) async throws -> RecommendationPage? {
        var items = [URLQueryItem(name: "size", value: String(size))]
        if bearer == nil {
            if !fieldTagIDs.isEmpty { items.append(URLQueryItem(name: "fieldTagIds", value: fieldTagIDs.map(String.init).joined(separator: ","))) }
            if !categories.isEmpty { items.append(URLQueryItem(name: "categories", value: categories.joined(separator: ","))) }
            if !traitTagIDs.isEmpty { items.append(URLQueryItem(name: "traitTagIds", value: traitTagIDs.map(String.init).joined(separator: ","))) }
        }
        let data: Data
        do { data = try await send(api, path: "v1/discovery/recommendations", items, bearer: bearer) } catch ActivityFetchError.notFound { return nil }
        let payload = try JSONDecoder().decode(RecommendationPayload.self, from: data)
        return RecommendationPage(items: payload.items.map { recommended($0, api: api) }, total: payload.total)
    }
    /// 맞춤 대기열 (contract "맞춤 대기열(스토리형 쇼츠)" 2026-10-24): `GET /v1/me/discovery-queue`, today's cards in order. Nil on 404 (a
    /// server without the queue yet): discovery hides the entry. A 401 throws `RecommendationFailure.unauthorized`. `seen` is not read.
    static func fetchDiscoveryQueue(api: URL, bearer: String) async throws -> [RecommendedActivity]? {
        let data: Data
        do { data = try await send(api, path: "v1/me/discovery-queue", [], bearer: bearer) } catch ActivityFetchError.notFound { return nil }
        return try JSONDecoder().decode(RecommendationPayload.self, from: data).items.map { recommended($0, api: api) }
    }
    /// `더 받기`: `POST /v1/me/discovery-queue/refill` rebuilds today's queue from the remaining candidates; the caller reads it again.
    static func refillDiscoveryQueue(api: URL, bearer: String) async throws {
        _ = try await send(api, path: "v1/me/discovery-queue/refill", [], method: "POST", body: Data("{}".utf8), bearer: bearer)
    }
    /// One 반응 (`POST /v1/me/recommendation-signals {activityId, kind, tagId?}`); `tagId` only for `LESS_FIELD`.
    static func sendRecommendationSignal(api: URL, activityID: String, kind: RecommendationSignal, tagID: Int64?, bearer: String) async throws {
        guard let id = Int64(activityID) else { return }
        let body = try JSONEncoder().encode(SignalInput(activityId: id, kind: kind.rawValue, tagId: tagID))
        _ = try await send(api, path: "v1/me/recommendation-signals", [], method: "POST", body: body, bearer: bearer)
    }
    private static func recommended(_ item: RecommendationPayload.Item, api: URL) -> RecommendedActivity {
        let program = item.program?.value
        func resolve(_ path: String?) -> URL? { path.flatMap { URL(string: $0, relativeTo: api)?.absoluteURL } }
        return RecommendedActivity(activity: model(item.activity), programName: program?.name ?? item.activity.program?.name, reasons: item.reasons,
                                   programID: program?.id, logoURL: resolve(program?.logoUrl), coverURL: resolve(program?.coverUrl),
                                   scenes: item.scenes?.value.map { $0.compactMap { $0.value?.scene } })
    }
    private struct SceneDTO: Decodable {
        let type: String
        let hook: String?; let name: String?; let items: [HighlightDTO]?; let text: String?
        let label: String?; let dday: Lenient<Int>?; let deadlineText: String?; let reasons: [String]?
        var scene: QueueScene? {
            switch type {
            case "HOOK": hook.map { .hook(hook: $0, name: name ?? "") }
            case "HIGHLIGHTS": .highlights((items ?? []).map(\.highlight))
            case "ELIGIBILITY":
                (items ?? []).isEmpty && text == nil ? nil
                    : .eligibility(items: (items ?? []).map { AudienceLine(icon: $0.icon.flatMap(AudienceLine.Icon.init(rawValue:)), text: $0.text) },
                                   text: text)
            case "BENEFIT": text.map(QueueScene.benefit)
            case "DEADLINE": .deadline(label: label.flatMap { $0.isEmpty ? nil : $0 }, days: dday?.value, deadlineText: deadlineText, reasons: reasons ?? [])
            default: nil
            }
        }
    }
    /// Nil fields are left out of the JSON (synthesized `encodeIfPresent`).
    private struct SignalInput: Encodable { let activityId: Int64; let kind: String; let tagId: Int64? }
    enum RecommendationFailure: Error { case unauthorized }
    /// The tag vocabulary for the 추천 funnel (`GET /v1/tags`) with each tag's kind and 상위 분야. Older servers send neither; such a tag is a 성격.
    static func fetchTagOptions(api: URL) async throws -> [TagOption] {
        try JSONDecoder().decode(TagOptionPayload.self, from: try await send(api, path: "v1/tags", [])).items.map {
            TagOption(id: $0.id, name: $0.name, nameEn: $0.nameEn, isField: $0.kind == "FIELD", parentID: $0.parentId)
        }
    }
    private struct RecommendationPayload: Decodable {
        /// `program` (ProgramSummary) rides beside the activity; its id, name, logo and cover are read, and a malformed one falls back to the activity's name.
        struct Item: Decodable {
            let activity: Activity; let program: Lenient<ProgramRef>?; let reasons: [String]
            /// 맞춤 대기열 only; a malformed scene is dropped, a malformed list reads as absent.
            let scenes: Lenient<[Lenient<SceneDTO>]>?
        }
        struct ProgramRef: Decodable { let id: Int64?; let name: String; let logoUrl: String?; let coverUrl: String? }
        let items: [Item]
        /// Every matching activity; absent on servers that do not count.
        let total: Int?
    }
    private struct TagOptionPayload: Decodable {
        struct Item: Decodable { let id: Int64; let name: String; let nameEn: String?; let kind: String?; let parentId: Int64? }
        let items: [Item]
    }
    private static func send(_ api: URL, path: String, _ items: [URLQueryItem], method: String = "GET", body: Data? = nil,
                             bearer: String? = nil) async throws -> Data {
        var components = URLComponents(url: api.appending(path: path), resolvingAgainstBaseURL: false)
        components?.queryItems = items.isEmpty ? nil : items
        guard let url = components?.url else { throw URLError(.badURL) }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        request.httpMethod = method
        if let body {
            request.httpBody = body
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(acceptLanguage(Locale.preferredLanguages), forHTTPHeaderField: "Accept-Language")
        if let bearer { request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization") }
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode
        if status == 404 { throw ActivityFetchError.notFound }
        if status == 401 { throw RecommendationFailure.unauthorized }
        guard let status, (200..<300).contains(status) else { throw URLError(.badServerResponse) }
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
        return ActivityPage(items: payload.items.map(model), nextCursor: payload.nextCursor)
    }
    private static func model(_ activity: Activity) -> ActivityModel {
        ActivityModel(id: String(activity.id), title: activity.name, summary: activity.description,
            organization: activity.organization.name, program: activity.program?.name,
            recruitmentStartAt: activity.application?.startAt, recruitmentEndAt: activity.application?.endAt,
            recruitmentStartDate: activity.application?.startDate, recruitmentEndDateExclusive: activity.application?.endDateExclusive,
            applicationStage: activity.application.map(stage),
            stages: activity.schedules.map(stage), activityStage: activity.activitySchedule.map(stage),
            officialUrl: activity.officialUrl, imageUrl: activity.imageUrl, applicationUrl: activity.applicationUrl,
            eligibility: activity.eligibility?.value.flatMap { $0.eligibility(depth: 0) },
            category: activity.category.flatMap(ActivityCategory.init(rawValue:)),
            // Sort key: the application's end; when no usable deadline exists, the activity start stands in for it.
            recruitmentDeadline: activity.application.flatMap { ActivityModel.instant($0.endAt) ?? ActivityModel.seoulDay($0.endDateExclusive) }
                ?? activity.activitySchedule.flatMap { ActivityModel.instant($0.startAt) ?? ActivityModel.seoulDay($0.startDate) },
            fieldTags: (activity.fieldTagsAll ?? activity.fieldTags ?? []).map(\.tag),
            traitTags: (activity.traitTags ?? activity.tags ?? []).map(\.tag),
            location: activity.location.flatMap { $0.isEmpty ? nil : $0 },
            place: activity.place?.value.map { ActivityPlace(name: $0.name, address: $0.address, lat: $0.lat, lng: $0.lng) },
            hook: activity.hook?.value.flatMap { $0.isEmpty ? nil : $0 },
            highlights: (activity.highlights?.value ?? []).filter { !$0.text.isEmpty }.map(\.highlight))
    }
    /// Stages keep every schedule: timed (either bound may be missing), all-day (either day may be missing), or date-less.
    /// An unknown kind reads as an event.
    private static func stage(_ schedule: Schedule) -> ActivityStageModel {
        let zone = schedule.timeZone.flatMap { TimeZone(identifier: $0) == nil ? nil : $0 } ?? "Asia/Seoul"
        let kind = schedule.kind.flatMap(ActivityStageModel.Kind.init(rawValue:)) ?? .event
        let startAt = ActivityModel.instant(schedule.startAt), endAt = ActivityModel.instant(schedule.endAt)
        if startAt != nil || endAt != nil {
            return ActivityStageModel(id: String(schedule.id), title: schedule.title, kind: kind, start: startAt, end: endAt,
                                      allDay: false, dateLabel: schedule.dateLabel, timeZone: zone, statuses: statuses(schedule),
                                      recurrence: ScheduleRecurrence(schedule.recurrence?.value))
        }
        let startDate = ActivityModel.seoulDay(schedule.startDate), endDate = ActivityModel.seoulDay(schedule.endDateExclusive)
        return ActivityStageModel(id: String(schedule.id), title: schedule.title, kind: kind, start: startDate, end: endDate,
                                  allDay: startDate != nil || endDate != nil, dateLabel: schedule.dateLabel, timeZone: "Asia/Seoul",
                                  statuses: statuses(schedule), overlapCheck: overlapCheck(schedule),
                                  recurrence: ScheduleRecurrence(schedule.recurrence?.value))
    }
    /// The schedule's statuses (contract "일정 상태" 2026-10-22); an unknown kind reads as OTHER, and none (older responses) the default three.
    private static func statuses(_ schedule: Schedule) -> [ScheduleStatus] {
        let items = (schedule.statuses?.value ?? []).map {
            ScheduleStatus(id: $0.id, name: $0.name, kind: ScheduleStatus.Kind(rawValue: $0.kind) ?? .other)
        }
        return items.isEmpty ? ScheduleStatus.defaults : items
    }
    private static func overlapCheck(_ schedule: Schedule) -> OverlapCheck? {
        schedule.overlapCheck?.value.map { OverlapCheck(startTime: $0.startTime, endTime: $0.endTime) }
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
        let openActivityCount: Int; let nearestRecruitmentEndAt: String?; let matchedActivityIds: [Int64]; let latestActivityAt: String?
        let upcomingActivityCount: Int?; let nearestRecruitmentStartAt: String?
        /// 분야 (at most two, relevance order) and 성격 tags (contract "분야·성격 태그와 아이콘" 2026-10-24); absent on older responses,
        /// which only send the mixed `tags`.
        let fieldTags: [TagDTO]?; let traitTags: [TagDTO]?; let tags: [TagDTO]?
        /// Program logo path, or nil without a logo (contract "프로그램 로고" 2026-10-19 밤); absent on older responses.
        let logoUrl: String?
    }
    private struct TagDTO: Decodable {
        let id: Int64; let name: String; let nameEn: String?; let kind: String?
        var tag: Tag { Tag(id: id, name: name, nameEn: nameEn, kind: kind.flatMap(Tag.Kind.init(rawValue:)) ?? .trait) }
    }
    private struct Named: Decodable { let name: String }
    private struct Schedule: Decodable {
        let id: Int64; let title: String; let kind: String?; let startAt: String?; let endAt: String?; let timeZone: String?
        let startDate: String?; let endDateExclusive: String?; let dateLabel: String
        /// 일정 상태·겹침 확인 시간 (2026-10-22); absent on older responses, and a malformed value reads as none.
        let statuses: Lenient<[StatusDTO]>?
        let overlapCheck: Lenient<OverlapDTO>?
        /// RRULE body (contract "반복 일정" 2026-10-22); absent on older responses.
        let recurrence: Lenient<String>?
    }
    private struct StatusDTO: Decodable { let id: Int64?; let name: String; let kind: String }
    private struct OverlapDTO: Decodable { let startTime: String; let endTime: String }
    private struct Activity: Decodable {
        let id: Int64; let name: String; let description: String; let organization: Named; let program: Named?
        let officialUrl: String; let imageUrl: String?
        /// Curated application link; absent on older responses.
        let applicationUrl: String?
        let application: Schedule?; let activitySchedule: Schedule?; let schedules: [Schedule]
        /// Unreadable eligibility JSON must not fail the whole activity.
        let eligibility: Lenient<EligibilityNode>?
        /// 활동 종류 code; absent on older responses.
        let category: String?
        /// 분야 (`fieldTagsAll` every one, `fieldTags` at most two) and 성격 tags (2026-10-24); absent on older responses, which send `tags`.
        let fieldTags: [TagDTO]?; let fieldTagsAll: [TagDTO]?; let traitTags: [TagDTO]?; let tags: [TagDTO]?
        /// 장소 (contract "활동 장소"); both absent on older responses. A malformed `place` reads as none.
        let location: String?
        let place: Lenient<PlaceDTO>?
        /// 한 줄 소개·핵심 세 줄 (contract "맞춤 대기열" 2026-10-24); absent on older responses, and a malformed value reads as none.
        let hook: Lenient<String>?
        let highlights: Lenient<[HighlightDTO]>?
    }
    private struct HighlightDTO: Decodable {
        let icon: String?; let text: String
        var highlight: ActivityHighlight { ActivityHighlight(icon: icon.flatMap(ActivityHighlight.Icon.init(rawValue:)), text: text) }
    }
    private struct PlaceDTO: Decodable { let name: String; let address: String; let lat: Double; let lng: Double }
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
