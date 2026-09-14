struct OrganizationModel: Codable, Identifiable {
    let id: String
    let name: String
    let parentId: String?
    private enum CodingKeys: String, CodingKey { case id, name; case parentId = "parentOrganizationId" }
}
