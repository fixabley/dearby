import Foundation
import SwiftData

/// Storage-only checks: no AppState, screen models or favorites participate in these transactions.
@MainActor
enum SnapshotTransactionChecks {
    static func run(snapshot: BundleSnapshot) throws {
        var failSave = false
        var saves = 0
        let store = try SwiftDataSnapshotStore(inMemory: true, commit: { context in
            saves += 1
            if failSave { throw TransactionCheckFailure.save }
            try context.save()
        })
        let id = snapshot.feedIDs[0]
        let orgID = snapshot.organizations[0].id
        let before = try store.withSnapshot(snapshot) { manifest in
            let source = SwiftDataNoticeSource(context: store.context,
                external: SnapshotNoticeSource(notices: snapshot.notices, sources: snapshot.sources), commit: store.persistCache)
            let organizations = SwiftDataOrganizationSource(context: store.context,
                external: SnapshotOrganizationSource(organizations: snapshot.organizations), commit: store.persistCache)
            _ = try source.fetch(id: id)
            _ = try organizations.fetch(id: orgID)
            precondition(saves == 0, "Promotions must stay in the enclosing snapshot transaction")
            return manifest
        }
        precondition(saves == 1)
        let noticeBytes = try store.context.fetch(FetchDescriptor<NoticeRecord>()).map(\.payload)
        let orgNames = try store.context.fetch(FetchDescriptor<OrganizationRecord>()).map(\.name)
        var replacement = snapshot
        replacement.contentHash = "transaction-replacement"
        do {
            _ = try store.withSnapshot(replacement) { _ -> SnapshotManifest in
                let empty = try store.context.fetchCount(FetchDescriptor<NoticeRecord>())
                precondition(empty == 0)
                throw TransactionCheckFailure.read
            }
            preconditionFailure("Candidate read failure accepted")
        } catch TransactionCheckFailure.read {}
        precondition(saves == 1)
        try assertPreserved()
        failSave = true
        do { _ = try store.prepare(replacement); preconditionFailure("Save failure accepted") } catch TransactionCheckFailure.save {}
        precondition(saves == 2)
        try assertPreserved()
        failSave = false
        _ = try store.withSnapshot(snapshot) { manifest in
            do { _ = try store.prepare(snapshot); preconditionFailure("Reentrant transaction accepted") } catch SnapshotStoreError.pendingChanges {}
            return manifest
        }
        precondition(saves == 2, "Same snapshot without promotions performs no save")
        store.context.insert(OrganizationRecord(OrganizationModel(id: "pending", name: "pending", parentId: nil)))
        do { _ = try store.prepare(snapshot); preconditionFailure("Pending context accepted") } catch SnapshotStoreError.pendingChanges {}
        precondition(store.context.hasChanges)
        store.context.rollback()
        try assertPreserved()
        let next = try store.prepare(replacement)
        let emptyNotices = try store.context.fetchCount(FetchDescriptor<NoticeRecord>())
        let emptyOrganizations = try store.context.fetchCount(FetchDescriptor<OrganizationRecord>())
        precondition(next.digest != before.digest && emptyNotices == 0 && emptyOrganizations == 0 && saves == 3)

        func assertPreserved() throws {
            let manifest = try store.manifest()
            let notices = try store.context.fetch(FetchDescriptor<NoticeRecord>()).map(\.payload)
            let organizations = try store.context.fetch(FetchDescriptor<OrganizationRecord>()).map(\.name)
            precondition(manifest == before && notices == noticeBytes && organizations == orgNames && !store.context.hasChanges)
            let reopened = ModelContext(store.container)
            let diskManifest = try reopened.fetch(FetchDescriptor<SnapshotManifestRecord>()).first?.value
            precondition(diskManifest == before)
        }
        print("PASS: snapshot transaction stages both L2 promotions; read/save rollback; no-op digest; pending/reentrant rejection; atomic replacement")
    }
}
private enum TransactionCheckFailure: Error { case read, save }
