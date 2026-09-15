import Foundation
import SwiftData

/// App composes independent entity stores in one explicit disk transaction; favorites are separate.
@MainActor
final class SwiftDataSnapshotStore {
    let container: ModelContainer
    let context: ModelContext
    private let commit: (ModelContext) throws -> Void

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

    /// Same digest does no writes. A new digest invalidates BOTH L2 slices and metadata atomically.
    @discardableResult
    func prepare(_ snapshot: BundleSnapshot) throws -> SnapshotManifest {
        guard snapshot.schemaVersion == "1.0.0", snapshot.mode == "reviewed_sample",
              Set(snapshot.notices.map(\.id)).count == snapshot.notices.count,
              Set(snapshot.organizations.map(\.id)).count == snapshot.organizations.count else { throw CocoaError(.coderReadCorrupt) }
        let next = try SnapshotManifest(snapshot: snapshot)
        if let current = try manifest(), current.digest == next.digest { return current }
        guard !context.hasChanges else { throw SnapshotStoreError.pendingChanges }
        do {
            try NoticeCacheStorage.deleteAll(in: context)
            try OrganizationCacheStorage.deleteAll(in: context)
            if let record = try context.fetch(FetchDescriptor<SnapshotManifestRecord>()).first {
                record.update(next)
            } else {
                context.insert(SnapshotManifestRecord(next))
            }
            try commit(context)
            return next
        } catch {
            context.rollback()
            throw error
        }
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

enum SnapshotStoreError: Error { case pendingChanges }
