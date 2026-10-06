import Foundation

// Example activities for screens that stay examples (received-card groups, calendar tests).
// Discovery no longer uses them; it reads GET /v1/catalog.
enum DemoActivities {
    static let all: [ActivityModel] = [
        activity(id: "conference", title: "Dearby 개발자 컨퍼런스", type: .registration, day: "2026-10-24", start: "13:00", end: "17:00"),
        activity(id: "camp", title: "Dearby 메이커 캠프", type: .selection, day: "2026-11-07", start: "10:00", end: "18:00"),
        activity(id: "meetup", title: "Dearby 커뮤니티 밋업", type: .registration, day: "2026-11-21", start: "14:00", end: "17:00")
    ]
    private static func activity(id: String, title: String, type: ActivityModel.Participation,
                                 day: String, start: String, end: String) -> ActivityModel {
        let label = "\(day) \(start)~\(end)"
        let formatter = ISO8601DateFormatter()
        let schedule = ActivityScheduleModel(id: id, title: title,
            start: formatter.date(from: "\(day)T\(start):00+09:00")!,
            end: formatter.date(from: "\(day)T\(end):00+09:00")!, dateLabel: label, timeZone: "Asia/Seoul")
        return ActivityModel(id: id, title: title, summary: "예시 활동입니다.", organization: "Dearby 커뮤니티",
            participationType: type, recruitmentStatus: "unknown", isRecruiting: false, freshness: "unavailable",
            recruitmentStartAt: nil, recruitmentEndAt: nil, sourceCheckedAt: nil, validUntil: nil, dateLabel: label,
            location: nil, cost: nil, audience: nil, roles: [], schedules: [schedule],
            officialUrl: "https://example.com", applicationUrl: nil, sourceNote: "")
    }
}
