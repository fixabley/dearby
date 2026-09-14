import Foundation

@MainActor
struct SnapshotNoticeSource: NoticeRecordSource {
    private let records: [String: NoticeModel]
    private let sources: [NoticeSource]
    init(notices: [NoticeModel], sources: [NoticeSource]) {
        records = Dictionary(notices.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        self.sources = sources
    }
    func fetch(id: String) -> NoticeModel? {
        guard var notice = records[id] else { return nil }
        let ids = Set(notice.sourceIds + notice.evidence.map(\.sourceId))
        notice.sources = sources.filter { ids.contains($0.id) }
        notice.evidence = notice.evidence.map { reference in
            var resolved = reference
            resolved.sourceURL = notice.sources.first { $0.id == reference.sourceId }.flatMap { URL(string: $0.url) }
            return resolved
        }
        return notice
    }
}
