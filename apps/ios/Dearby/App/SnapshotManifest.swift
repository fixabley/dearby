import Foundation
import CryptoKit
import SwiftData

struct SnapshotManifest: Equatable {
    let digest: String
    let schemaVersion: String
    let mode: String
    let snapshotAt: String
    let feedIDs: [String]
    let organizationIDs: [String]

    init(snapshot: BundleSnapshot) throws {
        schemaVersion = snapshot.schemaVersion; mode = snapshot.mode; snapshotAt = snapshot.snapshotAt
        feedIDs = snapshot.feedIDs; organizationIDs = snapshot.organizations.map(\.id)
        if !snapshot.contentHash.isEmpty {
            digest = "notice-codec-v1:" + snapshot.contentHash
        } else {
            // Programmatic fixtures have no wire bytes; hash all decoded fields deterministically.
            let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys]
            let input = Fingerprint(header: [schemaVersion, mode, snapshotAt], sources: snapshot.sources,
                organizations: snapshot.organizations, notices: try snapshot.notices.map(NoticeCacheStorage.fingerprint))
            digest = "notice-codec-v1:" + SHA256.hash(data: try encoder.encode(input)).map { String(format: "%02x", $0) }.joined()
        }
    }
    init(digest: String, schemaVersion: String, mode: String, snapshotAt: String, feedIDs: [String], organizationIDs: [String]) {
        self.digest = digest; self.schemaVersion = schemaVersion; self.mode = mode; self.snapshotAt = snapshotAt
        self.feedIDs = feedIDs; self.organizationIDs = organizationIDs
    }
    private struct Fingerprint: Encodable {
        let header: [String]
        let sources: [NoticeSource]
        let organizations: [OrganizationModel]
        let notices: [Data]
    }
}

@Model
final class SnapshotManifestRecord {
    @Attribute(.unique) var id: String
    var digest: String
    var schemaVersion: String
    var mode: String
    var snapshotAt: String
    var feedIDs: [String]
    var organizationIDs: [String]
    init(_ value: SnapshotManifest) {
        id = "current"; digest = value.digest; schemaVersion = value.schemaVersion
        mode = value.mode; snapshotAt = value.snapshotAt; feedIDs = value.feedIDs; organizationIDs = value.organizationIDs
    }
    var value: SnapshotManifest {
        SnapshotManifest(digest: digest, schemaVersion: schemaVersion, mode: mode, snapshotAt: snapshotAt, feedIDs: feedIDs, organizationIDs: organizationIDs)
    }
    func update(_ value: SnapshotManifest) {
        digest = value.digest; schemaVersion = value.schemaVersion; mode = value.mode
        snapshotAt = value.snapshotAt; feedIDs = value.feedIDs; organizationIDs = value.organizationIDs
    }
}
