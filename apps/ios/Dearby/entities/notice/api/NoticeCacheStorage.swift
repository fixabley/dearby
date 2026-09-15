import Foundation
import SwiftData

/// App composition contract. Record schema and codec stay inside the Notice slice.
@MainActor
enum NoticeCacheStorage {
    static var modelTypes: [any PersistentModel.Type] { [NoticeRecord.self] }

    /// Participates in the caller's snapshot transaction; does not save or promote L1.
    static func deleteAll(in context: ModelContext) throws {
        for record in try context.fetch(FetchDescriptor<NoticeRecord>()) { context.delete(record) }
    }

    /// Preserve the existing canonical bytes and notice-codec-v1 digest contract.
    nonisolated static func fingerprint(_ notice: NoticeModel) throws -> Data {
        try NoticeStorageCodec.encode(notice)
    }
}
