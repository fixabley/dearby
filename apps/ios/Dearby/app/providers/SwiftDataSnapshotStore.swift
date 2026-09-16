import Foundation
import SwiftData

/// App-owned disk snapshot metadata and atomic cache transactions.
@MainActor
final class SwiftDataSnapshotStore {
    let container: ModelContainer
    let context: ModelContext
    private let commit: (ModelContext) throws -> Void
    private var preparing = false

    init(url: URL? = nil, inMemory: Bool = false,
         commit: @escaping (ModelContext) throws -> Void = { try $0.save() }) throws {
        let schema = Schema(NoticeCacheStorage.modelTypes + OrganizationCacheStorage.modelTypes + [SnapshotManifestRecord.self])
        let configuration: ModelConfiguration
        if inMemory {
            configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true, cloudKitDatabase: .none)
        } else {
            let storeURL: URL
            if let url { storeURL = url } else {
                let directory = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask,
                    appropriateFor: nil, create: true).appendingPathComponent("DearbyNoticeCache", isDirectory: true)
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                storeURL = directory.appendingPathComponent("notices.store")
            }
            configuration = ModelConfiguration(schema: schema, url: storeURL, cloudKitDatabase: .none)
        }
        container = try ModelContainer(for: schema, configurations: [configuration])
        context = ModelContext(container); context.autosaveEnabled = false
        self.commit = commit
    }

    func manifest() throws -> SnapshotManifest? {
        var request = FetchDescriptor<SnapshotManifestRecord>()
        request.fetchLimit = 2
        let rows = try context.fetch(request)
        guard rows.count <= 1, rows.first == nil || rows.first?.id == "current" else { throw CocoaError(.coderReadCorrupt) }
        return rows.first?.value
    }

    /// Same digest does no writes. Changed metadata and both L2 slices commit together.
    @discardableResult
    func prepare(_ snapshot: BundleSnapshot) throws -> SnapshotManifest {
        try withSnapshot(snapshot) { $0 }
    }

    /// Synchronous, non-reentrant staging. The candidate is returned only after durable save.
    func withSnapshot<Value>(_ snapshot: BundleSnapshot, load: (SnapshotManifest) throws -> Value) throws -> Value {
        guard !preparing, !context.hasChanges else { throw SnapshotStoreError.pendingChanges }
        guard snapshot.schemaVersion == "1.0.0", snapshot.mode == "reviewed_sample",
              Set(snapshot.notices.map(\.id)).count == snapshot.notices.count,
              Set(snapshot.organizations.map(\.id)).count == snapshot.organizations.count else { throw CocoaError(.coderReadCorrupt) }
        let next = try SnapshotManifest(snapshot: snapshot)
        preparing = true
        defer { preparing = false }
        do {
            if try manifest()?.digest != next.digest {
                try NoticeCacheStorage.deleteAll(in: context)
                try OrganizationCacheStorage.deleteAll(in: context)
                if let record = try context.fetch(FetchDescriptor<SnapshotManifestRecord>()).first {
                    record.update(next)
                } else {
                    context.insert(SnapshotManifestRecord(next))
                }
            }
            let candidate = try load(next)
            if context.hasChanges { try commit(context) }
            return candidate
        } catch {
            context.rollback()
            throw error
        }
    }

    /// Candidate source promotions join the snapshot transaction; ordinary reads save immediately.
    func persistCache(_ cacheContext: ModelContext) throws {
        guard cacheContext === context else { throw SnapshotStoreError.foreignContext }
        if !preparing { try commit(context) }
    }

    func makeSession(snapshot: BundleSnapshot, favorites: FavoriteOrganizations) throws -> NoticeSession {
        let metadata = try prepare(snapshot)
        let noticeSource = SwiftDataNoticeSource(context: context,
            external: SnapshotNoticeSource(notices: snapshot.notices, sources: snapshot.sources))
        let organizationSource = SwiftDataOrganizationSource(context: context,
            external: SnapshotOrganizationSource(organizations: snapshot.organizations))
        return try NoticeSession(manifest: metadata, noticeSource: noticeSource, organizationSource: organizationSource, favorites: favorites)
    }
}

enum SnapshotStoreError: Error { case pendingChanges, foreignContext }
