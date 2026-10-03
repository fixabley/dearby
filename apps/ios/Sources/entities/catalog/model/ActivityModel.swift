import Foundation

struct ActivityModel: Equatable, Identifiable, Sendable {
    enum Participation: String, Sendable { case registration, selection }
    let id: String
    let title: String
    let summary: String
    let participationType: Participation
    let demoStatus: String
    let dateLabel: String
    let location: String
    let cost: String
    let audience: String
    let roles: [String]
    let schedules: [ActivityScheduleModel]
    let officialUrl: String
    var applicationUrl: String { officialUrl }

    static func safeURL(_ raw: String?) -> URL? {
        guard let raw, let url = URL(string: raw), url.scheme?.lowercased() == "https",
              let host = url.host, !host.isEmpty, url.user == nil, url.password == nil else { return nil }
        return url
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
