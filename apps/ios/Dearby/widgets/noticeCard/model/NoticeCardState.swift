struct NoticeCardState: Identifiable {
    let id: String
    let title: String
    let category: String
    let contextNames: String
    let targetUser: String
    let hasQualityIssues: Bool
    let organizationName: String?
    var saved: Bool
    var schedules: [NoticeCardScheduleState] = []
    var summary: NoticeCardSummaryState {
        NoticeCardSummaryState(id: id, title: title, category: category, contextNames: contextNames,
            targetUser: targetUser, hasQualityIssues: hasQualityIssues)
    }
}
