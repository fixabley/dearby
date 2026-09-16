// Force casts below assert the bundled JSON fixture schema, never external input.
import Foundation
import SwiftData

@main
struct SwiftDataSnapshotTests {
    @MainActor static func main() throws {
        let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
        let snapshot = try JSONDecoder().decode(BundleSnapshot.self, from: data)
        try SnapshotTransactionChecks.run(snapshot: snapshot)
        let folder = URL(fileURLWithPath: "apps/ios/build/snapshot-disk-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("cache.store")
        let favorites = FavoriteOrganizations(repository: DiskTestFavorites())
        let firstManifest: SnapshotManifest
        do {
            var saves = 0
            let store = try SwiftDataSnapshotStore(url: url, commit: { context in saves += 1; try context.save() })
            precondition(!store.context.autosaveEnabled)
            firstManifest = try store.prepare(snapshot)
            precondition(saves == 1)
            let empty = try store.context.fetchCount(FetchDescriptor<NoticeRecord>())
            precondition(empty == 0, "Prepare persists manifest only; L3 fallback must be reachable")
            let orgEmpty = try store.context.fetchCount(FetchDescriptor<OrganizationRecord>())
            precondition(orgEmpty == 0)
            _ = try store.prepare(snapshot)
            precondition(saves == 1, "Same seed must do no writes")
            let session = try store.makeSession(snapshot: snapshot, favorites: favorites)
            precondition(session.cards.count == snapshot.feedIDs.count && session.snapshotDate == snapshot.snapshotAt)
            let count = try store.context.fetchCount(FetchDescriptor<NoticeRecord>())
            precondition(count == snapshot.feedIDs.count)
            precondition(session.favoriteCards.compactMap(\.state).map(\.id) == ["db-insurance"])
        }
        let store = try SwiftDataSnapshotStore(url: url)
        let persistedManifest = try store.manifest()!
        precondition(persistedManifest == firstManifest)
        let noExternalNotice = NoExternalNotice()
        let noExternalOrganization = NoExternalOrganization()
        // Recreate a complete session from disk metadata + ID queries only, no bundle model reinsertion.
        let oldSession = try NoticeSession(manifest: persistedManifest,
            noticeSource: SwiftDataNoticeSource(context: store.context, external: noExternalNotice),
            organizationSource: SwiftDataOrganizationSource(context: store.context, external: noExternalOrganization), favorites: favorites)
        precondition(noExternalNotice.calls == 0 && noExternalOrganization.calls == 0)
        let oldFirst = oldSession.cards[0].state!
        let removedID = oldSession.cards[1].state!.id
        var raw = try JSONSerialization.jsonObject(with: data) as! [String: Any] // swiftlint:disable:this force_cast
        var rawNotices = raw["activities"] as! [[String: Any]] // swiftlint:disable:this force_cast
        rawNotices.removeAll { $0["id"] as? String == removedID }
        let index = rawNotices.firstIndex { $0["id"] as? String == oldFirst.id }!
        rawNotices[index]["title"] = "수정된 공고"
        raw["activities"] = rawNotices
        raw["snapshotAt"] = "2026-10-01T00:00:00+09:00"
        var rawOrganizations = raw["organizations"] as! [[String: Any]] // swiftlint:disable:this force_cast
        rawOrganizations.removeAll { $0["id"] as? String == "db-insurance" }
        let orgIndex = rawOrganizations.firstIndex { $0["id"] as? String == "krc" }!
        rawOrganizations[orgIndex]["name"] = "수정된 기관"
        rawOrganizations[orgIndex]["parentOrganizationId"] = "cbnu"
        raw["organizations"] = rawOrganizations
        let replacement = try JSONDecoder().decode(BundleSnapshot.self, from: JSONSerialization.data(withJSONObject: raw))
        // Simulate a failing explicit transaction while keeping a live old session/cache.
        let failing = try SwiftDataSnapshotStore(url: url, commit: { _ in throw SnapshotTestError.save })
        do { _ = try failing.prepare(replacement); preconditionFailure() } catch SnapshotTestError.save {}
        let afterFailure = try failing.manifest()
        precondition(afterFailure == persistedManifest && !failing.context.hasChanges)
        let oldOnDisk = try SwiftDataNoticeSource(context: failing.context, external: noExternalNotice).fetch(id: oldFirst.id)
        precondition(oldOnDisk?.title == oldFirst.title && oldSession.cards[0].state!.title == oldFirst.title)
        let cacheBefore = oldSession.notices.cachedNotice(oldFirst.id)
        precondition(cacheBefore?.title == oldFirst.title && favorites.ids == ["db-insurance"])
        let newSession = try store.makeSession(snapshot: replacement, favorites: favorites)
        precondition(newSession.cards[0].state!.title == "수정된 공고" && newSession.cards[0].state!.organizationName == "수정된 기관")
        precondition(newSession.detailState(oldFirst.id)!.organizationPath == ["충북대학교"])
        precondition(newSession.snapshotDate == replacement.snapshotAt && newSession.detailState(removedID) == nil)
        let missingNotice = try SwiftDataNoticeSource(context: store.context, external: NoNotice()).fetch(id: removedID)
        let missingOrganization = try SwiftDataOrganizationSource(context: store.context, external: NoOrganization()).fetch(id: "db-insurance")
        precondition(missingNotice == nil && missingOrganization == nil)
        precondition(favorites.ids == ["db-insurance"], "Snapshot deletion must not delete user's favorites IDs")
        let once = try store.context.fetch(FetchDescriptor<NoticeRecord>()).map(\.id)
        let organizationOnce = try store.context.fetch(FetchDescriptor<OrganizationRecord>()).map(\.id)
        _ = try store.prepare(replacement)
        let twice = try store.context.fetch(FetchDescriptor<NoticeRecord>()).map(\.id)
        precondition(Set(once) == Set(twice) && Set(once).count == once.count && Set(organizationOnce).count == organizationOnce.count)
        // Bad store location fails instead of deleting/resetting or fatalError.
        let blocked = folder.appendingPathComponent("not-a-directory")
        try Data("preserve".utf8).write(to: blocked)
        do { _ = try SwiftDataSnapshotStore(url: blocked.appendingPathComponent("store")); preconditionFailure() } catch {}
        let preserved = try String(contentsOf: blocked, encoding: .utf8)
        precondition(preserved == "preserve")
        print("PASS: disk manifest/session reopen without mock calls; same seed no writes; atomic change/deletion/metadata; save rollback preserves disk+L1; initialization failure preserves files/favorites")
    }
}
private enum SnapshotTestError: Error { case save, unexpectedExternal }
@MainActor private final class NoExternalNotice: NoticeRecordSource {
    var calls = 0
    func fetch(id: String) throws -> NoticeModel? { calls += 1; throw SnapshotTestError.unexpectedExternal }
}
@MainActor private final class NoExternalOrganization: OrganizationSource {
    var calls = 0
    func fetch(id: String) throws -> OrganizationModel? { calls += 1; throw SnapshotTestError.unexpectedExternal }
}
@MainActor private struct NoNotice: NoticeRecordSource { func fetch(id: String) -> NoticeModel? { nil } }
@MainActor private struct NoOrganization: OrganizationSource { func fetch(id: String) -> OrganizationModel? { nil } }
@MainActor private final class DiskTestFavorites: FavoriteOrganizationsRepository {
    func load() -> Set<String> { ["db-insurance"] }
    func save(_ ids: Set<String>) {}
}
