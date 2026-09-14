import Foundation

struct BundleNoticeCatalogRepository: NoticeCatalogRepository {
    let bundle: Bundle

    init(bundle: Bundle = .main) {
        self.bundle = bundle
    }

    func load() throws -> NoticeCatalog {
        guard let url = bundle.url(forResource: "activity-samples", withExtension: "json") else {
            throw CocoaError(.fileNoSuchFile)
        }
        let catalog = try JSONDecoder().decode(NoticeCatalog.self, from: Data(contentsOf: url))
        guard catalog.schemaVersion == "1.0.0", catalog.mode == "reviewed_sample" else {
            throw CocoaError(.coderReadCorrupt)
        }
        return catalog
    }
}
