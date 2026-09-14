import Foundation

@main
struct OrganizationRepositoryTests {
    @MainActor
    static func main() {
        let parent = OrganizationModel(id: "parent", name: "상위", parentId: nil)
        let child = OrganizationModel(id: "selected", name: "선택", parentId: "parent")
        let sibling = OrganizationModel(id: "sibling", name: "다른 선택", parentId: "parent")
        let source = CountingOrganizationSource([parent, child, sibling])
        let repository = OrganizationRepository(source: source)
        precondition(source.fetches.isEmpty, "Cache starts cold; initialization must not prewarm")
        precondition(repository.path(to: "selected").map(\.id) == ["parent", "selected"])
        precondition(source.fetches == ["selected": 1, "parent": 1])
        _ = repository.path(to: "selected")
        _ = repository.path(to: "sibling")
        precondition(source.fetches == ["selected": 1, "parent": 1, "sibling": 1])
        precondition(repository.organization(nil) == nil)
        precondition(repository.organization("missing") == nil)
        precondition(repository.organization("missing") == nil)
        precondition(source.fetches["missing"] == 2, "Missing records need not be cached")
        let replacement = CountingOrganizationSource([
            OrganizationModel(id: "selected", name: "새 이름", parentId: "new-parent"),
            OrganizationModel(id: "new-parent", name: "새 상위", parentId: nil),
            OrganizationModel(id: "parent", name: "이전 상위 이름 변경", parentId: nil)])
        repository.replaceSource(replacement)
        precondition(replacement.fetches.isEmpty)
        precondition(repository.path(to: "selected").map(\.name) == ["새 상위", "새 이름"])
        precondition(repository.organization("parent")?.name == "이전 상위 이름 변경")
        precondition(repository.organization("sibling") == nil)
        repository.replaceSource(CountingOrganizationSource([
            OrganizationModel(id: "a", name: "A", parentId: "b"),
            OrganizationModel(id: "b", name: "B", parentId: "a"),
            OrganizationModel(id: "orphan", name: "고아", parentId: "unknown")]))
        precondition(repository.path(to: "a").map(\.id) == ["b", "a"])
        precondition(repository.path(to: "orphan").map(\.id) == ["orphan"])
        precondition(repository.path(to: nil).isEmpty)
        print("PASS: independent source/cache cold miss/hit, shared parents, nil/missing, cycle/partial path, snapshot rename/reparent invalidation")
    }
}

@MainActor
private final class CountingOrganizationSource: OrganizationSource {
    private let records: [String: OrganizationModel]
    private(set) var fetches: [String: Int] = [:]
    init(_ records: [OrganizationModel]) { self.records = Dictionary(uniqueKeysWithValues: records.map { ($0.id, $0) }) }
    func fetch(id: String) -> OrganizationModel? {
        fetches[id, default: 0] += 1
        return records[id]
    }
}
