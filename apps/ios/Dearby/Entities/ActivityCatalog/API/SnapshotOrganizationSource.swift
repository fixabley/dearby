@MainActor
struct SnapshotOrganizationSource: OrganizationSource {
    private let records: [String: ActivityOrganization]

    init(organizations: [ActivityOrganization]) {
        records = Dictionary(organizations.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    func fetch(id: String) -> ActivityOrganization? { records[id] }
}
