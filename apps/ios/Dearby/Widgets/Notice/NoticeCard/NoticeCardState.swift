struct NoticeCardState: Identifiable {
    let id: String
    let title: String
    let category: String
    let contextNames: String
    let targetUser: String
    let applicationSummary: String
    let locationSummary: String
    let hasQualityIssues: Bool
    let organizationName: String?
    var saved: Bool
}
