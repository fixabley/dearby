import Foundation
import SwiftData

@MainActor
final class SwiftDataOrganizationSource: OrganizationSource {
    private let context: ModelContext
    private let external: any OrganizationSource
    private let commit: (ModelContext) throws -> Void
    init(context: ModelContext, external: any OrganizationSource,
         commit: @escaping (ModelContext) throws -> Void = { try $0.save() }) {
        self.context = context
        self.external = external
        self.commit = commit
        context.autosaveEnabled = false
    }
    func fetch(id: String) throws -> OrganizationModel? {
        var query = FetchDescriptor<OrganizationRecord>(predicate: #Predicate { $0.id == id })
        query.fetchLimit = 2
        let rows = try context.fetch(query)
        guard rows.count <= 1 else { throw CocoaError(.coderReadCorrupt) }
        if let row = rows.first { return OrganizationModel(id: row.id, name: row.name, parentId: row.parentId) }
        guard let value = try external.fetch(id: id) else { return nil }
        do {
            context.insert(OrganizationRecord(value))
            try commit(context)
            return value
        } catch {
            context.rollback()
            throw error
        }
    }
}
