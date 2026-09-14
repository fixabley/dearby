/// Independent record source; callers never receive an embedded organization tree.
@MainActor
protocol OrganizationSource {
    func fetch(id: String) -> NoticeOrganization?
}
