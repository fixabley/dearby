@MainActor
final class NoticeRepository {
    private var source: any NoticeRecordSource
    private var cache: [String: NoticeModel] = [:]
    init(source: any NoticeRecordSource) { self.source = source }
    func notice(_ id: String) throws -> NoticeModel? {
        if let notice = cache[id] { return notice }
        guard let notice = try source.fetch(id: id) else { return nil }
        cache[id] = notice
        return notice
    }
    /// Routing may inspect an already composed notice without performing disk IO in a View.
    func cachedNotice(_ id: String) -> NoticeModel? { cache[id] }
    func replaceSource(_ source: any NoticeRecordSource) {
        self.source = source
        cache.removeAll()
    }
}
