import Foundation

private enum ActivityFetchError: Error { case notFound }

/// `GET /v1/activities` and `GET /v1/discovery/programs`. Transport or decoding failure is an error, never an empty page.
enum ActivityClient {
    /// Activities, optionally those of one program (`programId`); `size` up to 100 so an expanded program loads in one page.
    /// `open` asks the server for currently open activities only (contract "열린 활동 판정 일원화"): the client never re-judges them.
    /// Tags are a search condition only: `q` matching a tag name is the server's rule, and `tagIds` is never sent (2026-10-19 밤).
    /// `kind` (발견 `프로그램 | 이벤트`) keeps an expanded program to that side's activities.
    static func fetch(api: URL, query: String, cursor: Int64?, programID: Int64? = nil, size: Int? = nil, open: Bool = false,
                      kind: DiscoveryKind? = nil) async throws -> ActivityPage {
        var items: [URLQueryItem] = []
        if let kind { items.append(URLQueryItem(name: "kind", value: kind.rawValue)) }
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
    /// `kind` (발견 `프로그램 | 이벤트`) limits the programs and their summary dates to that side.
    /// `logoUrl` is an API-origin path (`/v1/programs/{id}/logo?v=…`, contract "프로그램 로고"), resolved here against `api`.
    static func fetchPrograms(api: URL, query: String, cursor: Int64?, openOnly: Bool = true, kind: DiscoveryKind? = nil) async throws -> ProgramPage {
        var items: [URLQueryItem] = []
        if let kind { items.append(URLQueryItem(name: "kind", value: kind.rawValue)) }
        if !query.isEmpty { items.append(URLQueryItem(name: "q", value: query)) }
        if !openOnly { items.append(URLQueryItem(name: "openOnly", value: "false")) }
        if let cursor { items.append(URLQueryItem(name: "cursor", value: String(cursor))) }
        let payload = try JSONDecoder().decode(ProgramPayload.self, from: try await send(api, path: "v1/discovery/programs", items))
        return ProgramPage(items: payload.items.map { summary($0, api: api) }, nextCursor: payload.nextCursor)
    }
    private static func summary(_ program: Program, api: URL) -> ProgramSummary {
        ProgramSummary(id: program.id, name: program.name, nameEn: program.nameEn, organization: program.organization.name,
                       openActivityCount: program.openActivityCount ?? 0, nearestRecruitmentEndAt: program.nearestRecruitmentEndAt,
                       matchedActivityIDs: (program.matchedActivityIds ?? []).map(String.init), latestActivityAt: program.latestActivityAt,
                       upcomingActivityCount: program.upcomingActivityCount ?? 0, nearestRecruitmentStartAt: program.nearestRecruitmentStartAt,
                       nearestIsActivityStart: program.nearestIsActivityStart ?? false,
                       fieldTags: Array((program.fieldTags ?? []).prefix(2)).map(\.tag),
                       traitTags: (program.traitTags ?? program.tags ?? []).map(\.tag),
                       logoURL: program.logoUrl.flatMap { URL(string: $0, relativeTo: api)?.absoluteURL }, hasTimetable: program.hasTimetable ?? false)
    }
    /// 즐겨찾기 (contract "즐겨찾기(하트)·새 모집 푸시 알림" 2026-10-25): `GET /v1/me/favorites`, programs then activities in the server's
    /// order. Nil on 404 (a server without favorites yet). A malformed item is dropped. A 401 throws `RecommendationFailure.unauthorized`.
    static func fetchFavorites(api: URL, bearer: String) async throws -> FavoriteList? {
        let data: Data
        do { data = try await send(api, path: "v1/me/favorites", [], bearer: bearer) } catch ActivityFetchError.notFound { return nil }
        let payload = try JSONDecoder().decode(FavoritesPayload.self, from: data)
        return FavoriteList(
            programs: (payload.programs ?? []).compactMap(\.value).map {
                FavoriteProgram(program: summary($0.program, api: api), newRecruitment: $0.newRecruitment ?? false, level: NotifyLevel(raw: $0.notifyLevel))
            },
            activities: (payload.activities ?? []).compactMap(\.value).map {
                FavoriteActivity(activity: model($0.activity), programName: $0.program?.value?.name ?? $0.activity.program?.name,
                                 level: NotifyLevel(raw: $0.notifyLevel))
            })
    }
    /// `PUT /v1/me/favorites/{programs|activities}/{id} {notifyLevel?}` (204, idempotent): without a level the server keeps the one it has,
    /// or the default for a new favorite (contract "프로그램·활동별 알림 수준").
    static func putFavorite(api: URL, _ target: FavoriteTarget, level: NotifyLevel?, bearer: String) async throws {
        let body = try JSONEncoder().encode(LevelInput(notifyLevel: level?.rawValue))
        _ = try await send(api, path: "v1/me/favorites/\(target.path)", [], method: "PUT", body: body, bearer: bearer)
    }
    static func deleteFavorite(api: URL, _ target: FavoriteTarget, bearer: String) async throws {
        _ = try await send(api, path: "v1/me/favorites/\(target.path)", [], method: "DELETE", bearer: bearer)
    }
    /// The favorites screen opened the program: its `새 모집` badge goes away (`POST /v1/me/favorites/programs/{id}/seen`).
    static func markFavoriteProgramSeen(api: URL, programID: Int64, bearer: String) async throws {
        _ = try await send(api, path: "v1/me/favorites/programs/\(programID)/seen", [], method: "POST", body: Data("{}".utf8), bearer: bearer)
    }
    /// 알림 목록 (contract "알림 목록(발견 우상단 종)" 2026-10-25): `GET /v1/me/notifications?cursor=`, 20 program groups a page. Nil on 404
    /// (a server without the inbox yet). A malformed group or item is dropped.
    static func fetchInbox(api: URL, cursor: String?, bearer: String) async throws -> InboxPage? {
        let data: Data
        let items = cursor.map { [URLQueryItem(name: "cursor", value: $0)] } ?? []
        do { data = try await send(api, path: "v1/me/notifications", items, bearer: bearer) } catch ActivityFetchError.notFound { return nil }
        let payload = try JSONDecoder().decode(InboxPayload.self, from: data)
        return InboxPage(groups: (payload.groups ?? []).compactMap(\.value).map { group in
            InboxGroup(program: summary(group.program, api: api), unread: group.unread ?? 0, items: (group.items ?? []).compactMap(\.value).map {
                InboxItem(id: $0.id, kind: InboxItem.Kind(rawValue: $0.kind), activityID: String($0.activity.id), activityName: $0.activity.name,
                          createdAt: ActivityModel.instant($0.createdAt), read: $0.readAt != nil)
            })
        }, nextCursor: payload.nextCursor?.value)
    }
    /// The bell's badge (`GET /v1/me/notifications/unread-count`); nil on 404.
    static func fetchUnreadCount(api: URL, bearer: String) async throws -> Int? {
        let data: Data
        do { data = try await send(api, path: "v1/me/notifications/unread-count", [], bearer: bearer) } catch ActivityFetchError.notFound { return nil }
        return try JSONDecoder().decode(CountPayload.self, from: data).count
    }
    /// `POST /v1/me/notifications/{id}/read`, or every one with `id` nil (`read-all`).
    static func markNotificationsRead(api: URL, id: Int64?, bearer: String) async throws {
        let path = id.map { "v1/me/notifications/\($0)/read" } ?? "v1/me/notifications/read-all"
        _ = try await send(api, path: path, [], method: "POST", body: Data("{}".utf8), bearer: bearer)
    }
    /// 트랙·세션 시간표 (contract "트랙·세션 시간표" 2026-10-26): `GET /v1/activities/:id/schedules/:scheduleId/timetable`, read without a
    /// token like discovery (the hearts read `SessionFavoritesModel`). Nil on 404 (unpublished, deleted, or no sessions). A malformed session
    /// or track is dropped.
    static func fetchTimetable(api: URL, activityID: String, scheduleID: String) async throws -> Timetable? {
        let data: Data
        do { data = try await send(api, path: "v1/activities/\(activityID)/schedules/\(scheduleID)/timetable", []) } catch ActivityFetchError.notFound { return nil }
        let payload = try JSONDecoder().decode(TimetablePayload.self, from: data)
        return Timetable(activityID: activityID, scheduleID: scheduleID, scheduleTitle: payload.schedule.title, zone: payload.schedule.zone,
                         tracks: payload.tracks.compactMap(\.value).map(\.track).sorted { $0.position < $1.position },
                         sessions: payload.sessions.compactMap { $0.value?.session }.sorted { $0.start < $1.start })
    }
    /// 내 시간표 (`GET /v1/me/session-favorites`), session start order, each with its block's zone and track (2026-10-26 `schedule`·`track`,
    /// so no timetable is read again; an older response reads as Seoul without a track). Nil on 404 (a server without session favorites).
    static func fetchSessionFavorites(api: URL, bearer: String) async throws -> [FavoriteSession]? {
        let data: Data
        do { data = try await send(api, path: "v1/me/session-favorites", [], bearer: bearer) } catch ActivityFetchError.notFound { return nil }
        return try JSONDecoder().decode(SessionFavoritesPayload.self, from: data).items.compactMap(\.value).compactMap { item in
            item.session.session.map {
                FavoriteSession(activityID: String(item.activityId), activityName: item.activityName, scheduleID: String(item.scheduleId),
                                scheduleTitle: item.scheduleTitle, session: $0, track: item.track?.value?.track,
                                zone: item.schedule?.value?.zone ?? "Asia/Seoul")
            }
        }
    }
    /// `PUT` (on) or `DELETE` (off) `/v1/me/session-favorites/:sessionId`, 204 either way.
    static func setSessionFavorite(api: URL, sessionID: Int64, on: Bool, bearer: String) async throws {
        _ = try await send(api, path: "v1/me/session-favorites/\(sessionID)", [], method: on ? "PUT" : "DELETE", bearer: bearer)
    }
    private struct TimetablePayload: Decodable {
        struct Track: Decodable {
            let id: Int64; let name: String; let room: String?; let position: Int
            var track: TimetableTrack { TimetableTrack(id: id, name: name, room: room, position: position) }
        }
        let schedule: Schedule
        let tracks: [Lenient<Track>]
        let sessions: [Lenient<SessionDTO>]
    }
    private struct SessionDTO: Decodable {
        struct Speaker: Decodable { let name: String; let affiliation: String? }
        let id: Int64; let trackId: Int64?; let kind: String; let title: String; let description: String?
        let startAt: String; let endAt: String; let speakers: [Speaker]?; let tags: [TagDTO]?
        /// Nil when a time is unreadable.
        var session: TimetableSession? {
            guard let start = ActivityModel.instant(startAt), let end = ActivityModel.instant(endAt) else { return nil }
            return TimetableSession(id: id, trackID: trackId, kind: TimetableSession.Kind(rawValue: kind) ?? .other, title: title,
                                    description: description.flatMap { $0.isEmpty ? nil : $0 }, start: start, end: end,
                                    speakers: (speakers ?? []).map { .init(name: $0.name, affiliation: $0.affiliation.flatMap { $0.isEmpty ? nil : $0 }) },
                                    tags: (tags ?? []).map(\.tag))
        }
    }
    private struct SessionFavoritesPayload: Decodable {
        struct Item: Decodable {
            let activityId: Int64; let activityName: String; let scheduleId: Int64; let scheduleTitle: String; let session: SessionDTO
            let schedule: Lenient<Schedule>?; let track: Lenient<TimetablePayload.Track>?
        }
        let items: [Lenient<Item>]
    }
    private struct InboxPayload: Decodable {
        struct Group: Decodable { let program: Program; let unread: Int?; let items: [Lenient<Item>]? }
        struct Item: Decodable {
            struct Ref: Decodable { let id: Int64; let name: String }
            let id: Int64; let kind: String; let activity: Ref; let createdAt: String?; let readAt: String?
        }
        let groups: [Lenient<Group>]?
        /// A string or a number on the wire; kept as text and sent back as is.
        let nextCursor: Cursor?
    }
    private struct Cursor: Decodable {
        let value: String
        init(from decoder: Decoder) throws {
            let container = try decoder.singleValueContainer()
            value = try (try? container.decode(String.self)) ?? String(container.decode(Int64.self))
        }
    }
    private struct CountPayload: Decodable { let count: Int }
    private struct FavoritesPayload: Decodable {
        struct ProgramItem: Decodable { let program: Program; let newRecruitment: Bool?; let notifyLevel: String? }
        struct ActivityItem: Decodable {
            let activity: Activity; let program: Lenient<Named>?; let notifyLevel: String?
        }
        let programs: [Lenient<ProgramItem>]?
        let activities: [Lenient<ActivityItem>]?
    }
    /// A nil level is left out of the JSON (`{}`).
    private struct LevelInput: Encodable { let notifyLevel: String? }
    /// 추천 (contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24): `GET /v1/discovery/recommendations`, signed in only (contract
    /// "비로그인에서 만들기·추천을 막음" 2026-10-25): `bearer` is the access token and the server uses the saved preferences and 지원 자격 정보.
    /// Nil on 404 (a server without recommendations yet): the caller reads it as none. A 401 throws `RecommendationFailure.unauthorized`.
    static func fetchRecommendations(api: URL, size: Int, bearer: String) async throws -> RecommendationPage? {
        let data: Data
        do { data = try await send(api, path: "v1/discovery/recommendations", [URLQueryItem(name: "size", value: String(size))], bearer: bearer) } catch ActivityFetchError.notFound { return nil }
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
                                      recurrence: ScheduleRecurrence(schedule.recurrence?.value), hasTimetable: schedule.hasTimetable ?? false)
        }
        let startDate = ActivityModel.seoulDay(schedule.startDate), endDate = ActivityModel.seoulDay(schedule.endDateExclusive)
        return ActivityStageModel(id: String(schedule.id), title: schedule.title, kind: kind, start: startDate, end: endDate,
                                  allDay: startDate != nil || endDate != nil, dateLabel: schedule.dateLabel, timeZone: "Asia/Seoul",
                                  statuses: statuses(schedule), overlapCheck: overlapCheck(schedule),
                                  recurrence: ScheduleRecurrence(schedule.recurrence?.value), hasTimetable: schedule.hasTimetable ?? false)
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
        /// Both absent outside discovery (a favorite's ProgramSummary); read as 0 and none.
        let openActivityCount: Int?; let nearestRecruitmentEndAt: String?; let matchedActivityIds: [Int64]?; let latestActivityAt: String?
        let upcomingActivityCount: Int?; let nearestRecruitmentStartAt: String?; let nearestIsActivityStart: Bool?
        /// 분야 (at most two, relevance order) and 성격 tags (contract "분야·성격 태그와 아이콘" 2026-10-24); absent on older responses,
        /// which only send the mixed `tags`.
        let fieldTags: [TagDTO]?; let traitTags: [TagDTO]?; let tags: [TagDTO]?
        /// Program logo path, or nil without a logo (contract "프로그램 로고" 2026-10-19 밤); absent on older responses.
        let logoUrl: String?
        /// An open or upcoming activity has a timetable (2026-10-26); absent on older responses.
        let hasTimetable: Bool?
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
        /// 트랙·세션 시간표 (2026-10-26); absent on older responses.
        let hasTimetable: Bool?
        /// The zone session times are shown in: a known `timeZone`, else Seoul (an all-day block's).
        var zone: String { timeZone.flatMap { TimeZone(identifier: $0) == nil ? nil : $0 } ?? "Asia/Seoul" }
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
