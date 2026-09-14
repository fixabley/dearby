import Foundation
import SwiftData

@main
struct SwiftDataNoticeTests {
    @MainActor static func main() throws {
        let snapshot = try JSONDecoder().decode(BundleSnapshot.self, from: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1])))
        let mock = SnapshotNoticeSource(notices: snapshot.notices, sources: snapshot.sources)
        let folder = URL(fileURLWithPath: "apps/ios/build/notice-disk-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let config = ModelConfiguration(url: folder.appendingPathComponent("store"), cloudKitDatabase: .none)
        do {
            let container = try ModelContainer(for: NoticeRecord.self, configurations: config)
            let context = ModelContext(container); context.autosaveEnabled = false
            let external = CountExternal(mock)
            let disk = CountDisk(SwiftDataNoticeSource(context: context, external: external))
            let repository = NoticeRepository(source: disk)
            precondition(disk.calls == 0 && external.calls == 0)
            for notice in snapshot.notices {
                let value = try repository.notice(notice.id)!
                _ = try repository.notice(notice.id)
                let expected = mock.fetch(id: notice.id)!
                let encoded = try NoticeStorageCodec.encode(expected)
                precondition(tryEqual(value, expected))
                let keys = Set((try JSONSerialization.jsonObject(with: encoded) as! [String: Any]).keys)
                let storedFields = Mirror(reflecting: expected).children.compactMap { child -> String? in
                    // JSON omits nil optionals, but every populated stored domain field must be represented.
                    let mirror = Mirror(reflecting: child.value)
                    return mirror.displayStyle == .optional && mirror.children.isEmpty ? nil : child.label
                }
                precondition(Set(storedFields).isSubset(of: keys), "Codec must preserve all stored NoticeModel fields")
            }
            precondition(disk.calls == snapshot.notices.count && external.calls == snapshot.notices.count)
            let count = try context.fetchCount(FetchDescriptor<NoticeRecord>())
            precondition(count == snapshot.notices.count)
        }
        // Actual disk reopen, without inserting snapshot data again; external must not be called.
        let container = try ModelContainer(for: NoticeRecord.self, configurations: config)
        let context = ModelContext(container); context.autosaveEnabled = false
        let external = CountExternal(mock)
        let repository = NoticeRepository(source: SwiftDataNoticeSource(context: context, external: external))
        for notice in snapshot.notices {
            let restored = try repository.notice(notice.id)!
            precondition(tryEqual(restored, mock.fetch(id: notice.id)!))
        }
        precondition(external.calls == 0)
        let missing = try repository.notice("missing")
        precondition(missing == nil && external.calls == 1)
        // Corruption is a storage error, never a miss or silent external fallback.
        let record = try context.fetch(FetchDescriptor<NoticeRecord>())[0]
        record.payload = Data("corrupt".utf8); try context.save()
        let corrupted = SwiftDataNoticeSource(context: context, external: external)
        do { _ = try corrupted.fetch(id: record.id); preconditionFailure() } catch {}
        precondition(external.calls == 1)
        let removedID = record.id
        context.delete(record); try context.save()
        let failing = NoticeRepository(source: SwiftDataNoticeSource(context: context, external: external, commit: { _ in throw DiskTestError.failure }))
        let calls = external.calls
        for _ in 0..<2 {
            do { _ = try failing.notice(removedID); preconditionFailure() } catch DiskTestError.failure {}
        }
        precondition(external.calls == calls + 2 && failing.cachedNotice(removedID) == nil)
        let absent = try SwiftDataNoticeSource(context: context, external: EmptyNoticeSource()).fetch(id: removedID)
        precondition(absent == nil, "Failed promotion rolls back the disk row")
        let failedExternal = SwiftDataNoticeSource(context: context, external: FailedNoticeSource())
        do { _ = try failedExternal.fetch(id: removedID); preconditionFailure() } catch DiskTestError.failure {}
        print("PASS: per-ID SwiftData notice L1/L2/L3, disk reopen without external fetch, full codec metadata and corruption/missing distinction")
    }
    private static func tryEqual(_ left: NoticeModel, _ right: NoticeModel) -> Bool {
        do { return try NoticeStorageCodec.encode(left) == NoticeStorageCodec.encode(right) }
        catch { preconditionFailure("Codec failure: \(error)") }
    }
}
@MainActor private final class CountExternal: NoticeRecordSource {
    let mock: SnapshotNoticeSource
    var calls = 0
    init(_ mock: SnapshotNoticeSource) { self.mock = mock }
    func fetch(id: String) -> NoticeModel? { calls += 1; return mock.fetch(id: id) }
}
@MainActor private final class CountDisk: NoticeRecordSource {
    let disk: SwiftDataNoticeSource
    var calls = 0
    init(_ disk: SwiftDataNoticeSource) { self.disk = disk }
    func fetch(id: String) throws -> NoticeModel? { calls += 1; return try disk.fetch(id: id) }
}

private enum DiskTestError: Error { case failure }
@MainActor private struct EmptyNoticeSource: NoticeRecordSource {
    func fetch(id: String) -> NoticeModel? { nil }
}
@MainActor private struct FailedNoticeSource: NoticeRecordSource {
    func fetch(id: String) throws -> NoticeModel? { throw DiskTestError.failure }
}
