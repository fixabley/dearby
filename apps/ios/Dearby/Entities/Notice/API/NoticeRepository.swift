@MainActor
final class NoticeRepository {
    private var source: any NoticeRecordSource
    private var cache: [String: NoticeModel] = [:]
    init(source: any NoticeRecordSource) { self.source = source }
    func notice(_ id: String) -> NoticeModel? {
        if let notice = cache[id] { return notice }
        guard let notice = source.fetch(id: id) else { return nil }
        cache[id] = notice
        return notice
    }
    func replaceSource(_ source: any NoticeRecordSource) {
        self.source = source
        cache.removeAll()
    }
}
