import Foundation

protocol ActivityCatalogRepository {
    func load() throws -> ActivityCatalog
}
