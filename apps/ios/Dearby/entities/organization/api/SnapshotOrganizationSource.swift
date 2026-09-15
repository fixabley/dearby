@MainActor
struct SnapshotOrganizationSource: OrganizationSource {
    private let records: [String: OrganizationModel]

    init(organizations: [OrganizationModel]) {
        records = Dictionary(organizations.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    func fetch(id: String) -> OrganizationModel? { records[id] }
}
