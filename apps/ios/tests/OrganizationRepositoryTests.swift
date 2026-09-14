import Foundation

@main
struct OrganizationRepositoryTests {
    @MainActor
    static func main() {
        let parent = ActivityOrganization(id: "parent", name: "상위", parentOrganizationId: nil)
        let child = ActivityOrganization(id: "selected", name: "선택", parentOrganizationId: "parent")
        let sibling = ActivityOrganization(id: "sibling", name: "다른 선택", parentOrganizationId: "parent")
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
            ActivityOrganization(id: "selected", name: "새 이름", parentOrganizationId: "new-parent"),
            ActivityOrganization(id: "new-parent", name: "새 상위", parentOrganizationId: nil),
            ActivityOrganization(id: "parent", name: "이전 상위 이름 변경", parentOrganizationId: nil)])
        repository.replaceSource(replacement)
        precondition(replacement.fetches.isEmpty)
        precondition(repository.path(to: "selected").map(\.name) == ["새 상위", "새 이름"])
        precondition(repository.organization("parent")?.name == "이전 상위 이름 변경")
        precondition(repository.organization("sibling") == nil)
        repository.replaceSource(CountingOrganizationSource([
            ActivityOrganization(id: "a", name: "A", parentOrganizationId: "b"),
            ActivityOrganization(id: "b", name: "B", parentOrganizationId: "a"),
            ActivityOrganization(id: "orphan", name: "고아", parentOrganizationId: "unknown")]))
        precondition(repository.path(to: "a").map(\.id) == ["b", "a"])
        precondition(repository.path(to: "orphan").map(\.id) == ["orphan"])
        precondition(repository.path(to: nil).isEmpty)
        print("PASS: independent source/cache cold miss/hit, shared parents, nil/missing, cycle/partial path, snapshot rename/reparent invalidation")
    }
}

@MainActor
private final class CountingOrganizationSource: OrganizationSource {
    private let records: [String: ActivityOrganization]
    private(set) var fetches: [String: Int] = [:]
    init(_ records: [ActivityOrganization]) { self.records = Dictionary(uniqueKeysWithValues: records.map { ($0.id, $0) }) }
    func fetch(id: String) -> ActivityOrganization? {
        fetches[id, default: 0] += 1
        return records[id]
    }
}
