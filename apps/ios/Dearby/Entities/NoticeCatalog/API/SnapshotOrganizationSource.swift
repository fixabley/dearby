@MainActor
struct SnapshotOrganizationSource: OrganizationSource {
    private let records: [String: NoticeOrganization]

    init(organizations: [NoticeOrganization]) {
        records = Dictionary(organizations.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    func fetch(id: String) -> NoticeOrganization? { records[id] }
}
