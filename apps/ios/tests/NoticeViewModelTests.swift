// Force casts below assert the bundled JSON fixture schema, never external input.
import Foundation
import Observation
import Synchronization

@main
struct NoticeViewModelTests {
    @MainActor
    static func main() throws {
        let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
        let snapshot = try JSONDecoder().decode(BundleSnapshot.self, from: data)
        let source = CountNoticeSource(SnapshotNoticeSource(notices: snapshot.notices, sources: snapshot.sources))
        let orgSource = CountOrganizationSource(snapshot.organizations)
        let notices = NoticeRepository(source: source)
        let organizations = OrganizationRepository(source: orgSource)
        precondition(source.counts.isEmpty && orgSource.counts.isEmpty)
        let krc = snapshot.notices.first { $0.favoriteOrganizationId == "krc" }!
        let contest = snapshot.notices.first { $0.favoriteOrganizationId == "yeongnam-cyber-defense" }!
        precondition(testValue(try notices.notice("missing")) == nil && testValue(try notices.notice("missing")) == nil)
        precondition(source.counts["missing"] == 2, "Only success is cached")
        let model = testValue(try notices.notice(krc.id))!
        let reordered = SnapshotNoticeSource(notices: snapshot.notices, sources: snapshot.sources.reversed()).fetch(id: krc.id)!
        precondition(reordered.sourceURL == model.sourceURL, "Primary source follows reference ID, not source array order")
        let withoutPrimary = SnapshotNoticeSource(notices: snapshot.notices, sources: snapshot.sources.filter { $0.id != krc.sourceIds.first }).fetch(id: krc.id)!
        precondition(withoutPrimary.sourceURL == nil, "Missing primary never falls back to an unrelated source")
        precondition(orgSource.counts.isEmpty, "Notice lookup must not fetch organizations")
        _ = testValue(try notices.notice(krc.id))
        precondition(source.counts[krc.id] == 1)
        let memory = MemoryFavorites()
        let favorites = FavoriteOrganizations(repository: memory)
        let card = testValue(try NoticeCardViewModel(id: krc.id, notices: notices, organizations: organizations, favorites: favorites))
        let second = testValue(try NoticeCardViewModel(id: krc.id, notices: notices, organizations: organizations, favorites: favorites))
        let detail = testValue(try NoticeDetailViewModel(id: krc.id, notices: notices, organizations: organizations, favorites: favorites))
        let reopened = testValue(try NoticeDetailViewModel(id: krc.id, notices: notices, organizations: organizations, favorites: favorites))
        let favoriteCard = testValue(try FavoriteOrganizationCardViewModel(id: "krc", noticeIDs: snapshot.feedIDs, notices: notices, organizations: organizations, favorites: favorites))
        precondition(source.counts[krc.id] == 1 && orgSource.counts["krc"] == 1 && orgSource.counts["cbnu"] == 1)
        precondition(card.state!.organizationName == "한국농어촌공사" && card.state!.contextNames == "충북대학교")
        precondition(card.state!.title == krc.title && card.state!.targetUser == krc.targetUser)
        precondition(detail.state!.organizationPath.isEmpty && detail.state!.contexts[0].label == "행사 관련 기관")
        precondition(detail.state!.applicationURL?.absoluteString == krc.applicationInformation.url)
        precondition(detail.state!.application.time.timeline != nil)
        precondition(detail.state!.organizationLinks.allSatisfy { $0.organizationName != nil })
        precondition(detail.state!.sources.count == reopened.state!.sources.count && detail.state!.evidence.count == model.evidence.count)
        let cardRows = card.state!.schedules
        precondition(cardRows.count == krc.schedule.count + 1 && cardRows[0].title == "신청 기간")
        precondition(cardRows[0].places.allSatisfy { $0.venueIndex == nil })
        precondition(cardRows[0].places[0].text.contains(URL(string: krc.applicationInformation.url!)!.host!))
        precondition(!cardRows[0].places[0].text.contains(krc.applicationInformation.url!))
        for (input, expected) in [
            ("https://example.com/application/deep/path?form=123#section", "example.com"),
            ("  HTTP://www.example.com:8080/path?q=1  ", "www.example.com"),
            ("https://example.com/a%20b", "example.com"),
            ("서울시 중구 세종대로 110", "서울시 중구 세종대로 110"),
            ("신청: https://example.com/form", "신청: https://example.com/form"),
            ("https://example.com/form 방문 접수", "https://example.com/form 방문 접수"),
            ("https://example.com/form\n방문 접수", "https://example.com/form\n방문 접수"),
            ("www.example.com/form", "www.example.com/form"),
            ("mailto:apply@example.com", "mailto:apply@example.com"),
            ("https:///", "https:///"),
            ("", "")
        ] {
            precondition(NoticeCardScheduleState.placeLabel(input) == expected, input)
        }
        let contestRows = NoticeCardScheduleState.project(contest)
        precondition(contestRows.count == contest.schedule.count + 1)
        for (index, phase) in contest.schedules.enumerated() {
            let row = contestRows[index + 1]
            precondition(row.id == index && row.title == phase.period.label)
            if phase.period.mode == "online" {
                precondition(row.places.allSatisfy { $0.venueIndex == nil })
                precondition(row.places[0].text.contains("온라인"))
                if let url = phase.period.onlineUrl, let host = URL(string: url)?.host {
                    precondition(row.places[0].fields == ["온라인", host])
                }
            }
            for place in row.places where place.venueIndex != nil {
                precondition(phase.locations[place.venueIndex!].coordinates != nil)
                precondition(place.text.contains(phase.locations[place.venueIndex!].name))
            }
        }
        precondition(!cardRows[0].period.contains("한국 시간"))
        let foreign = NoticeCardScheduleState.period(start: "2026-09-15T14:00:00+09:00", end: "2026-09-15T15:00:00+09:00", timezone: "America/New_York", fallback: "")
        precondition(foreign.joined().components(separatedBy: "America/New_York").count == 2)
        let dateOnly = NoticeCardScheduleState.period(start: "2026-09-15", end: nil, timezone: nil, fallback: "일정 미확인")
        precondition(dateOnly == ["2026.9.15 (시간 미확인)부터", "종료 미확인"])
        precondition(NoticeCardScheduleState.period(start: nil, end: "2026-09-16", timezone: nil, fallback: "미확인").first == "시작 미확인")
        precondition(NoticeCardScheduleState.period(start: nil, end: nil, timezone: nil, fallback: "일정 미확인") == ["일정 미확인"])
        precondition(NoticeCardScheduleState.period(start: "2026-09-15T14:00:00+09:00", end: nil, timezone: "Bad/Zone", fallback: "").joined().contains("시간대 확인 필요"))
        precondition(NoticeCardScheduleState.period(start: "2026-09-15T14:00:00+09:00", end: nil, timezone: nil, fallback: "").joined().contains("+09:00"))
        precondition(NoticeCardScheduleState.period(start: "2026-09-17", end: "2026-09-15", timezone: nil, fallback: "").contains("기간 순서 확인 필요"))
        let boundaries = NoticeCardScheduleState.period(start: "2026-09-15T14:00:00+09:00", end: "2026-09-15T15:00:00+09:00", timezone: "Asia/Seoul", fallback: "")
        precondition(boundaries == ["2026.9.15 14:00부터", "2026.9.15 15:00까지"])
        for (index, phase) in model.schedules.enumerated() where phase.period.mode != "online" {
            for place in cardRows[index + 1].places where place.venueIndex != nil {
                let venue = phase.locations[place.venueIndex!]
                let routed = card.venue(scheduleIndex: index, venueIndex: place.venueIndex!)
                precondition(routed?.name == venue.name && routed?.coordinates == venue.coordinates)
                precondition(place.fields == [venue.name, venue.address].compactMap { $0 }.filter { !$0.isEmpty })
            }
        }
        let notifications = Mutex(0)
        withObservationTracking { _ = card.state?.saved; _ = detail.state?.saved; _ = favoriteCard.state } onChange: { notifications.withLock { $0 += 1 } }
        guard case .saved(let name) = second.save() else { preconditionFailure() }
        precondition(name == "한국농어촌공사" && card.state!.saved && detail.state!.saved && favoriteCard.state != nil)
        precondition(notifications.withLock { $0 } == 1)
        _ = second.save()
        precondition(memory.writes == 2 && favorites.ids == ["krc"])
        favoriteCard.remove()
        precondition(!card.state!.saved && !detail.state!.saved && favoriteCard.state == nil && memory.ids.isEmpty)
        let before = (source.counts, orgSource.counts)
        for _ in 0..<3 { _ = card.state; _ = detail.state; _ = favoriteCard.state }
        precondition(source.counts == before.0 && orgSource.counts == before.1, "Rendering reads never fetch")
        precondition(testValue(try NoticeCardViewModel(id: "absent", notices: notices, organizations: organizations, favorites: favorites)).state == nil)
        precondition(testValue(try NoticeDetailViewModel(id: "absent", notices: notices, organizations: organizations, favorites: favorites)).state == nil)
        let contestState = testValue(try NoticeDetailViewModel(id: contest.id, notices: notices, organizations: organizations, favorites: favorites)).state!
        precondition(contestState.organizationPath == testValue(try organizations.path(to: contest.favoriteOrganizationId)).dropLast().map(\.name))
        precondition(contestState.edition == 2)
        let projectedContest = testValue(try notices.notice(contest.id))!
        precondition(projectedContest.schedules.first { $0.period.phase == "preliminary" }!.locations.isEmpty)
        precondition(projectedContest.schedules.first { $0.period.phase == "final" }!.locations.map(\.name) == contest.location.venues.map(\.name))
        precondition(contestState.schedules.count == projectedContest.schedules.count)
        for (index, phase) in projectedContest.schedules.enumerated() {
            let row = contestState.schedules[index]
            precondition(row.period == CompactPeriod.period(start: phase.period.startsAt ?? phase.period.startsOn, end: phase.period.endsAt ?? phase.period.endsOn, timezone: phase.period.timezone, fallback: "일정 미확인"))
            precondition(row.title.contains(phase.period.label))
            precondition(row.venues.map(\.name) == phase.locations.filter { $0.coordinates != nil }.map(\.name))
            if phase.period.mode != "online" && phase.locations.isEmpty { precondition(row.location == "장소 미확인") }
            if phase.period.mode == "online" { precondition(row.title.hasPrefix("온라인") && row.location == "온라인" && row.venues.isEmpty) }
        }
        precondition(CompactPeriod.period(start: "2026-12-31", end: "2027-01-01", fallback: "unknown") == "2026.12.31 (시간 미확인) – 2027.1.1 (시간 미확인)")
        precondition(CompactPeriod.period(start: nil, end: nil, fallback: "확인 필요") == "확인 필요")
        precondition(CompactPeriod.period(start: "not-a-date", end: nil, fallback: "unknown").contains("not-a-date"))
        precondition(CompactPeriod.period(start: nil, end: "2026-09-15", fallback: "unknown") == "~ 2026.9.15 (시간 미확인)")
        precondition(CompactPeriod.period(start: "2026-09-15T14:00:00+09:00", end: "2026-09-15T16:00:00+09:00", timezone: "Asia/Seoul", fallback: "unknown") == "2026.9.15 14:00–16:00 (한국 시간)")
        precondition(CompactPeriod.period(start: "2026-09-15T14:00:12+09:00", end: nil, timezone: "Asia/Seoul", fallback: "unknown").contains("14:00:12"))
        precondition(CompactPeriod.period(start: "2026-02-30T14:00:00+09:00", end: nil, timezone: "Asia/Seoul", fallback: "unknown").contains("2026-02-30T14:00:00+09:00"))
        precondition(CompactPeriod.period(start: "2026-09-16", end: "2026-09-15", fallback: "unknown").contains("기간 순서 확인 필요"))
        precondition(CompactPeriod.period(start: "2026-09-15T14:00:00+09:00", end: nil, timezone: "Bad/Zone", fallback: "unknown").contains("시간대 확인 필요"))
        // Full evidence, including nested paths and unknown source, survives provider resolution.
        var raw = try JSONSerialization.jsonObject(with: data) as! [String: Any] // swiftlint:disable:this force_cast
        let rawNotices = raw["activities"] as! [[String: Any]] // swiftlint:disable:this force_cast
        for notice in snapshot.notices {
            let loaded = testValue(try notices.notice(notice.id))!
            precondition(loaded.evidence.count == evidenceCount(rawNotices.first { $0["id"] as? String == notice.id }!))
            precondition(loaded.sources.allSatisfy { $0.kind != nil && $0.checkedAt != nil })
            for evidence in loaded.evidence {
                precondition(!evidence.locator.isEmpty && !evidence.fieldPath.isEmpty)
                precondition(evidence.sourceURL?.absoluteString == snapshot.sources.first { $0.id == evidence.sourceId }?.url)
            }
            let fields = Set(Mirror(reflecting: loaded).children.compactMap(\.label))
            precondition(fields.isDisjoint(with: ["organization", "organizationName", "organizationPath", "organizations"]), "One NoticeModel holds only organization references")
        }
        var urlFixture = rawNotices.first { $0["id"] as? String == contest.id }!
        let fullURL = "https://online.example.com/join/long-path?room=123#entry"
        let address = "서울시 중구 세종대로 110"
        let prose = "https://example.com/form 방문 접수"
        var application = urlFixture["application"] as! [String: Any] // swiftlint:disable:this force_cast
        application["url"] = fullURL
        application["submissionLocations"] = [address, prose, "https://apply.example.com/path?q=1"]
        urlFixture["application"] = application
        var schedules = urlFixture["schedule"] as! [[String: Any]] // swiftlint:disable:this force_cast
        schedules[0]["mode"] = "online"
        schedules[0]["onlineUrl"] = fullURL
        urlFixture["schedule"] = schedules
        let urlModel = try JSONDecoder().decode(NoticeModel.self, from: JSONSerialization.data(withJSONObject: urlFixture))
        let urlRows = NoticeCardScheduleState.project(urlModel)
        precondition(urlRows[0].places[0].fields == [address, prose, "apply.example.com", "online.example.com"])
        precondition(urlRows[1].places[0].fields == ["온라인", "online.example.com"])
        precondition(urlModel.applicationInformation.url == fullURL && urlModel.schedule[0].onlineUrl == fullURL)
        precondition(urlModel.applicationInformation.submissionLocations == [address, prose, "https://apply.example.com/path?q=1"])
        print("PASS: card web URL hosts; address/prose and original application/schedule URLs preserved")
        var changedNotices = rawNotices
        let index = changedNotices.firstIndex { $0["id"] as? String == krc.id }!
        changedNotices[index]["favoriteOrganizationId"] = "unknown"
        changedNotices[index]["organizationLinks"] = [["organizationId": "unknown", "role": "contact", "basis": "source", "note": "보존"]]
        changedNotices[index]["evidence"] = [["sourceId": "missing-source", "locator": "보존"]]
        changedNotices[index]["title"] = "바뀐 공고"
        raw["activities"] = changedNotices
        var changedOrgs = raw["organizations"] as! [[String: Any]] // swiftlint:disable:this force_cast
        let orgIndex = changedOrgs.firstIndex { $0["id"] as? String == "cbnu" }!
        changedOrgs[orgIndex]["name"] = "바뀐 학교"
        changedOrgs[orgIndex]["parentOrganizationId"] = "unknown-parent"
        raw["organizations"] = changedOrgs
        let changed = try JSONDecoder().decode(BundleSnapshot.self, from: JSONSerialization.data(withJSONObject: raw))
        let nextSource = CountNoticeSource(SnapshotNoticeSource(notices: changed.notices, sources: changed.sources))
        let nextOrgs = CountOrganizationSource(changed.organizations)
        notices.replaceSource(nextSource); organizations.replaceSource(nextOrgs)
        precondition(nextSource.counts.isEmpty && nextOrgs.counts.isEmpty)
        let unresolved = testValue(try NoticeCardViewModel(id: krc.id, notices: notices, organizations: organizations, favorites: favorites))
        let changedDetail = testValue(try NoticeDetailViewModel(id: krc.id, notices: notices, organizations: organizations, favorites: favorites)).state!
        let writes = memory.writes
        guard case .unresolved = unresolved.save() else { preconditionFailure() }
        precondition(memory.writes == writes && unresolved.state!.organizationName == nil && unresolved.state!.contextNames == "바뀐 학교")
        precondition(changedDetail.organizationLinks[0].organizationID == "unknown" && changedDetail.organizationLinks[0].role == "contact" && changedDetail.organizationLinks[0].organizationName == nil)
        precondition(changedDetail.organizationLinks[0].basis == "source" && changedDetail.organizationLinks[0].note == "보존")
        precondition(changedDetail.evidence.contains { $0.sourceId == "missing-source" && $0.sourceURL == nil })
        precondition(nextSource.counts[krc.id] == 1 && testValue(try organizations.path(to: "cbnu")).map(\.name) == ["바뀐 학교"])
        let freshNotices = NoticeRepository(source: nextSource)
        let freshOrganizations = OrganizationRepository(source: nextOrgs)
        let freshCard = testValue(try NoticeCardViewModel(id: krc.id, notices: freshNotices, organizations: freshOrganizations, favorites: favorites))
        let freshDetail = testValue(try NoticeDetailViewModel(id: krc.id, notices: freshNotices, organizations: freshOrganizations, favorites: favorites))
        precondition(freshCard.state!.title == "바뀐 공고")
        precondition(freshDetail.state!.contexts[0].organizationName == "바뀐 학교")
        precondition(testValue(try NoticeDetailViewModel(id: "absent", notices: freshNotices, organizations: freshOrganizations, favorites: favorites)).state == nil)
        print("PASS: independent lazy notice/org sources, hit/reuse/replacement; one model, full evidence; card/detail/favorite states and shared Observation save/delete; missing IDs and snapshot recompose")
    }
    private static func evidenceCount(_ value: Any) -> Int {
        if let object = value as? [String: Any] {
            return object.reduce(0) { $0 + ((["evidence", "coordinateEvidence"].contains($1.key)) ? ($1.value as! [Any]).count : evidenceCount($1.value)) } // swiftlint:disable:this force_cast
        }
        return (value as? [Any])?.reduce(0) { $0 + evidenceCount($1) } ?? 0
    }
}
@MainActor private final class CountNoticeSource: NoticeRecordSource {
    let underlying: any NoticeRecordSource
    var counts: [String: Int] = [:]
    init(_ underlying: any NoticeRecordSource) { self.underlying = underlying }
    func fetch(id: String) throws -> NoticeModel? { counts[id, default: 0] += 1; return try underlying.fetch(id: id) }
}
@MainActor private final class CountOrganizationSource: OrganizationSource {
    let records: [OrganizationModel]
    var counts: [String: Int] = [:]
    init(_ records: [OrganizationModel]) { self.records = records }
    func fetch(id: String) -> OrganizationModel? { counts[id, default: 0] += 1; return records.first { $0.id == id } }
}
@MainActor private final class MemoryFavorites: FavoriteOrganizationsRepository {
    var ids: Set<String> = []
    var writes = 0
    func load() -> Set<String> { ids }
    func save(_ ids: Set<String>) { self.ids = ids; writes += 1 }
}

private func testValue<T>(_ operation: @autoclosure () throws -> T) -> T {
    do { return try operation() } catch { preconditionFailure("Unexpected error: \(error)") }
}
