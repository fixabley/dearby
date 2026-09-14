import Foundation

protocol NoticeCatalogRepository {
    func load() throws -> NoticeCatalog
}
