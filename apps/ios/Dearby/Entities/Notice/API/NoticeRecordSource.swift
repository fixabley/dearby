@MainActor
protocol NoticeRecordSource {
    func fetch(id: String) -> NoticeModel?
}
