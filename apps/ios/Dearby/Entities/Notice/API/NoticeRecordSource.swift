@MainActor
protocol NoticeRecordSource {
    func fetch(id: String) throws -> NoticeModel?
}
