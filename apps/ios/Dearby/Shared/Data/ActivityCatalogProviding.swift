import Foundation

protocol ActivityCatalogProviding {
    func load() throws -> ActivityCatalog
}
