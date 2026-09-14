import Foundation
import CryptoKit

/// Transport composition only. The activities wire key remains compatible.
struct BundleSnapshot: Decodable {
    let schemaVersion: String
    let mode: String
    let snapshotAt: String
    let sources: [NoticeSource]
    let organizations: [OrganizationModel]
    let notices: [NoticeModel]
    var contentHash: String = ""
    private enum CodingKeys: String, CodingKey {
        case schemaVersion, mode, snapshotAt, sources, organizations
        case notices = "activities"
    }
    var feedIDs: [String] {
        notices.filter(\.demoVisible).sorted {
            ($0.favoriteOrganizationId == nil ? 1 : 0) < ($1.favoriteOrganizationId == nil ? 1 : 0)
        }.map(\.id)
    }
}

protocol SnapshotReader { func load() throws -> BundleSnapshot }
struct BundleSnapshotReader: SnapshotReader {
    let bundle: Bundle
    init(bundle: Bundle = .main) { self.bundle = bundle }
    func load() throws -> BundleSnapshot {
        guard let url = bundle.url(forResource: "activity-samples", withExtension: "json") else { throw CocoaError(.fileNoSuchFile) }
        let data = try Data(contentsOf: url)
        var snapshot = try JSONDecoder().decode(BundleSnapshot.self, from: data)
        snapshot.contentHash = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        guard snapshot.schemaVersion == "1.0.0", snapshot.mode == "reviewed_sample" else { throw CocoaError(.coderReadCorrupt) }
        return snapshot
    }
}
