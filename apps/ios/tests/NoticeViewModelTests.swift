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
        precondition(card.state!.title == krc.title && card.state!.targetUser == krc.targetUser && card.state!.applicationSummary == krc.applicationInformation.summary)
        precondition(detail.state!.organizationPath.isEmpty && detail.state!.contexts[0].label == "행사 관련 기관")
        precondition(detail.state!.applicationURL?.absoluteString == krc.applicationInformation.url)
        precondition(detail.state!.applicationTime.timeline != nil)
        precondition(detail.state!.organizationLinks.allSatisfy { $0.organizationName != nil })
        precondition(detail.state!.sources.count == reopened.state!.sources.count && detail.state!.evidence.count == model.evidence.count)
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
        var raw = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        let rawNotices = raw["activities"] as! [[String: Any]]
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
        var changedNotices = rawNotices
        let index = changedNotices.firstIndex { $0["id"] as? String == krc.id }!
        changedNotices[index]["favoriteOrganizationId"] = "unknown"
        changedNotices[index]["organizationLinks"] = [["organizationId": "unknown", "role": "contact", "basis": "source", "note": "보존"]]
        changedNotices[index]["evidence"] = [["sourceId": "missing-source", "locator": "보존"]]
        changedNotices[index]["title"] = "바뀐 공고"
        raw["activities"] = changedNotices
        var changedOrgs = raw["organizations"] as! [[String: Any]]
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
        let session = testValue(try NoticeSession(snapshot: changed, favorites: favorites))
        precondition(session.cards.first { $0.state?.id == krc.id }!.state!.title == "바뀐 공고")
        precondition(session.detailState(krc.id)!.contexts[0].organizationName == "바뀐 학교")
        precondition(session.detailState("absent") == nil)
        print("PASS: independent lazy notice/org sources, hit/reuse/replacement; one model, full evidence; card/detail/favorite states and shared Observation save/delete; missing IDs and snapshot recompose")
    }
    private static func evidenceCount(_ value: Any) -> Int {
        if let object = value as? [String: Any] {
            return object.reduce(0) { $0 + ((["evidence", "coordinateEvidence"].contains($1.key)) ? ($1.value as! [Any]).count : evidenceCount($1.value)) }
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
