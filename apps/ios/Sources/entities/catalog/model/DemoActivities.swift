import Foundation

// Deliberately fixed examples: dates never change the displayed recruitment status.
enum DemoActivities {
    // Same on Android: the app starts with one example application.
    static let appliedIDs: Set<String> = ["conference"]
    static let all: [ActivityModel] = [
        activity(id: "conference", title: "Dearby 개발자 컨퍼런스", type: .registration,
                 status: "예시 모집중", day: "2026-10-24", start: "13:00", end: "17:00",
                 location: "서울 코엑스", audience: "개발자·디자이너·기획자", roles: ["개발자", "디자이너", "기획자"]),
        activity(id: "camp", title: "Dearby 메이커 캠프", type: .selection,
                 status: "예시 모집중", day: "2026-11-07", start: "10:00", end: "18:00",
                 location: "서울", audience: "개발·디자인·기획", roles: ["개발", "디자인", "기획"]),
        activity(id: "meetup", title: "Dearby 커뮤니티 밋업", type: .registration,
                 status: "예시 모집예정", day: "2026-11-21", start: "14:00", end: "17:00",
                 location: "온라인", audience: "누구나 (예시)", roles: [])
    ]
    private static func activity(id: String, title: String, type: ActivityModel.Participation,
                                 status: String, day: String, start: String, end: String,
                                 location: String, audience: String, roles: [String]) -> ActivityModel {
        let label = "\(day) \(start)~\(end)"
        let formatter = ISO8601DateFormatter()
        let schedule = ActivityScheduleModel(id: id, title: title,
            start: formatter.date(from: "\(day)T\(start):00+09:00")!,
            end: formatter.date(from: "\(day)T\(end):00+09:00")!, dateLabel: label, timeZone: "Asia/Seoul")
        return ActivityModel(id: id, title: title, summary: "화면 탐색과 신청 흐름을 체험하기 위한 예시 활동입니다.",
            participationType: type, demoStatus: status, dateLabel: label, location: location,
            cost: "무료", audience: audience, roles: roles, schedules: [schedule],
            officialUrl: "https://example.com")
    }
}
