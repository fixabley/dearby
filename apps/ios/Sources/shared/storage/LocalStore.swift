import Foundation
import SwiftData

@Model final class StoredDocument {
    @Attribute(.unique) var key: String
    var data: Data
    init(key: String, data: Data) { self.key = key; self.data = data }
}

@MainActor final class LocalStore {
    let container: ModelContainer
    private let context: ModelContext
    // Injection exercises save failure without replacing the persistence implementation.
    var beforeSave: (() throws -> Void)?
    init(container: ModelContainer) {
        self.container = container
        context = ModelContext(container)
        context.autosaveEnabled = false
    }
    convenience init() throws {
        try self.init(container: ModelContainer(for: StoredDocument.self))
    }
    func read<T: Decodable>(_ type: T.Type, key: String) throws -> T? {
        let target = key
        let rows = try context.fetch(FetchDescriptor<StoredDocument>(predicate: #Predicate { $0.key == target }))
        return try rows.first.map { try JSONDecoder().decode(type, from: $0.data) }
    }
    func write<T: Encodable>(_ value: T, key: String) throws {
        let data = try JSONEncoder().encode(value)
        let target = key
        do {
            if let row = try context.fetch(FetchDescriptor<StoredDocument>(predicate: #Predicate { $0.key == target })).first {
                row.data = data
            } else { context.insert(StoredDocument(key: key, data: data)) }
            try beforeSave?()
            try context.save()
        } catch { context.rollback(); throw error }
    }
}
