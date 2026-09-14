import Foundation
import SwiftData

@MainActor
final class SwiftDataNoticeSource: NoticeRecordSource {
    private let context: ModelContext
    private let external: any NoticeRecordSource
    private let commit: (ModelContext) throws -> Void
    init(context: ModelContext, external: any NoticeRecordSource,
         commit: @escaping (ModelContext) throws -> Void = { try $0.save() }) {
        self.context = context; self.external = external; self.commit = commit
        context.autosaveEnabled = false
    }
    func fetch(id: String) throws -> NoticeModel? {
        var query = FetchDescriptor<NoticeRecord>(predicate: #Predicate { $0.id == id })
        query.fetchLimit = 2
        let rows = try context.fetch(query)
        guard rows.count <= 1 else { throw CocoaError(.coderReadCorrupt) }
        if let row = rows.first {
            let value = try NoticeStorageCodec.decode(row.payload)
            guard value.id == id else { throw CocoaError(.coderReadCorrupt) }
            return value
        }
        guard let value = try external.fetch(id: id) else { return nil }
        guard value.id == id else { throw CocoaError(.coderReadCorrupt) }
        let payload = try NoticeStorageCodec.encode(value)
        do {
            context.insert(NoticeRecord(id: id, payload: payload))
            try commit(context)
            return value
        } catch {
            context.rollback()
            throw error
        }
    }
}
