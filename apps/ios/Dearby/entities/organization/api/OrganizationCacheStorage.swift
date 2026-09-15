import SwiftData

/// App composition contract; the persisted record remains slice-internal.
@MainActor
enum OrganizationCacheStorage {
    static var modelTypes: [any PersistentModel.Type] { [OrganizationRecord.self] }

    /// Participates in the caller's snapshot transaction; caller commits or rolls back both slices.
    static func deleteAll(in context: ModelContext) throws {
        for record in try context.fetch(FetchDescriptor<OrganizationRecord>()) { context.delete(record) }
    }
}
