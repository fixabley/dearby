import Foundation

/// One activity from `GET /v1/catalog` (contract catalog-v1). Missing values stay nil and read as unconfirmed.
struct ActivityModel: Equatable, Identifiable, Sendable {
    enum Participation: String, Sendable, Decodable { case registration, selection }
    let id: String
    let title: String
    let summary: String
    let organization: String?
    let participationType: Participation
    let recruitmentStatus: String
    let isRecruiting: Bool
    let freshness: String
    // Raw ISO 8601 strings: a present but unreadable time must not count as absent.
    let recruitmentStartAt: String?
    let recruitmentEndAt: String?
    let sourceCheckedAt: String?
    let validUntil: String?
    let dateLabel: String
    let location: String?
    let cost: String?
    let audience: String?
    let roles: [String]
    /// Timed schedules only; date-only schedules stay in `dateLabel`.
    let schedules: [ActivityScheduleModel]
    let officialUrl: String
    let applicationUrl: String?
    let sourceNote: String

    /// Same rule as web discovery (`apps/web/src/lib/models.ts` `isRecruiting`).
    func isOpen(at now: Date) -> Bool {
        guard isRecruiting, recruitmentStatus == "open", freshness == "verified",
              let checked = Self.instant(sourceCheckedAt), checked <= now,
              let valid = Self.instant(validUntil), now < valid else { return false }
        if recruitmentStartAt != nil { guard let start = Self.instant(recruitmentStartAt), start <= now else { return false } }
        if recruitmentEndAt != nil { guard let end = Self.instant(recruitmentEndAt), now < end else { return false } }
        return true
    }
    func statusLabel(at now: Date) -> String {
        if isOpen(at: now) { return "모집 중" }
        switch recruitmentStatus {
        case "scheduled": return "모집 예정"
        case "closed": return "모집 마감"
        default: return "모집 여부 확인 필요"
        }
    }
    /// The official application link, only while recruiting and only over https.
    func applicationURL(at now: Date) -> URL? { isOpen(at: now) ? Self.safeURL(applicationUrl) : nil }
    /// Quick apply on the discovery card: registration activities only.
    func quickApplyURL(at now: Date) -> URL? { participationType == .registration ? applicationURL(at: now) : nil }
    var recruitmentEnd: Date? { Self.instant(recruitmentEndAt) }

    static func safeURL(_ raw: String?) -> URL? {
        guard let raw, let url = URL(string: raw), url.scheme?.lowercased() == "https",
              let host = url.host, !host.isEmpty, url.user == nil, url.password == nil else { return nil }
        return url
    }
    static func instant(_ raw: String?) -> Date? {
        guard let raw else { return nil }
        let precise = ISO8601DateFormatter()
        precise.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return precise.date(from: raw) ?? ISO8601DateFormatter().date(from: raw)
    }
}
struct ActivityScheduleModel: Equatable, Identifiable, Sendable {
    let id: String
    let title: String
    let start: Date
    let end: Date
    let dateLabel: String
    let timeZone: String
}
