struct NoticeIdentityState {
    let organizationName: String?
    let organizationPath: [String]
    let categorySummary: String
    let contexts: [NoticeInstitutionState]
    let edition: Int?
}

struct NoticeInstitutionState {
    let organizationID: String
    let role: String
    let label: String
    let basis: String?
    let note: String?
    let organizationName: String?
}
