import Foundation

struct ActivityModel: Codable, Equatable, Identifiable, Sendable {
    enum Participation: String, Codable, Sendable { case registration, selection }
    enum Recruitment: String, Codable, Sendable { case open, scheduled, closed, unknown }
    enum Freshness: String, Codable, Sendable { case verified, stale, unavailable }
    let id: String
    let programId: String
    let organizationId: String
    let title: String
    let summary: String
    let participationType: Participation
    let recruitmentStatus: Recruitment
    let isRecruiting: Bool
    let recruitmentStartAt: String?
    let recruitmentEndAt: String?
    let dateLabel: String
    let location: String?
    let cost: String?
    let audience: String?
    let qualification: String?
    let roles: [String]
    let schedules: [ActivityScheduleModel]
    let officialUrl: String
    let applicationUrl: String?
    let sourceCheckedAt: String?
    let validUntil: String?
    let freshness: Freshness
    let sourceNote: String
    var imageUrl: String?
    var isPreview: Bool?

    func isCurrent(at now: Date) -> Bool {
        guard freshness == .verified, let checked = CatalogModel.date(sourceCheckedAt),
              let until = CatalogModel.date(validUntil), checked <= now, now < until,
              now.timeIntervalSince(checked) < 86_400 else { return false }
        return true
    }
    func isOpen(at now: Date) -> Bool {
        guard isRecruiting, recruitmentStatus == .open, isCurrent(at: now) else { return false }
        if let start = recruitmentStartAt {
            guard let date = CatalogModel.date(start), date <= now else { return false }
        }
        if let end = recruitmentEndAt {
            guard let date = CatalogModel.date(end), now < date else { return false }
        }
        return true
    }
    func status(at now: Date) -> String {
        if let end = CatalogModel.date(recruitmentEndAt), now >= end { return "모집 마감" }
        guard isCurrent(at: now) else { return "최신 모집 상태 미확인" }
        if isOpen(at: now) { return "모집 중" }
        switch recruitmentStatus {
        case .closed: return "모집 마감"
        case .scheduled: return "모집 예정"
        case .open, .unknown: return "모집 상태 미확인"
        }
    }
    static func safeURL(_ raw: String?) -> URL? {
        guard let raw, let url = URL(string: raw), ["https", "http"].contains(url.scheme?.lowercased() ?? ""),
              let host = url.host, !host.isEmpty, url.user == nil, url.password == nil else { return nil }
        return url
    }
}
struct ActivityScheduleModel: Codable, Equatable, Identifiable, Sendable {
    let id: String
    let title: String
    let startAt: String?
    let endAt: String?
    let dateLabel: String
    let timeZone: String
}
