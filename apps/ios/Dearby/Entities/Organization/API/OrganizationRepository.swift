/// MainActor confinement makes source replacement and cache invalidation atomic to readers.
@MainActor
final class OrganizationRepository {
    private var source: any OrganizationSource
    private var cache: [String: OrganizationModel] = [:]

    init(source: any OrganizationSource) { self.source = source }

    func organization(_ id: String?) -> OrganizationModel? {
        guard let id else { return nil }
        if let record = cache[id] { return record }
        guard let record = source.fetch(id: id) else { return nil }
        cache[id] = record
        return record
    }

    func path(to id: String?) -> [OrganizationModel] {
        var result: [OrganizationModel] = []
        var visited: Set<String> = []
        var next = id
        while let id = next, visited.insert(id).inserted, let record = organization(id) {
            result.insert(record, at: 0)
            next = record.parentId
        }
        return result
    }

    func replaceSource(_ source: any OrganizationSource) {
        self.source = source
        cache.removeAll()
    }
}
