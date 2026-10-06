import Foundation

/// Test-only `GET /v1/catalog` body (contract catalog-v1). Never part of the app target.
/// Times are fixed far ahead so recruiting stays open; the conference overlaps the example busy hour.
enum CatalogFixture {
    static let conference = "b1000000-0000-4000-8000-000000000001"
    static let camp = "b1000000-0000-4000-8000-000000000002"
    static let scheduled = "b1000000-0000-4000-8000-000000000003"
    static let stale = "b1000000-0000-4000-8000-000000000004"
    static let applyURL = "https://apply.example.test/conference"

    static func activity(_ id: String, _ title: String, type: String = "registration", status: String = "open",
                         recruiting: Bool = true, freshness: String = "verified", day: String, start: String, end: String,
                         application: String? = applyURL) -> [String: Any] {
        [
            "id": id, "programId": "c1000000-0000-4000-8000-000000000001", "organizationId": "a1000000-0000-4000-8000-000000000001",
            "title": title, "summary": "\(title) 소개", "participationType": type, "recruitmentStatus": status,
            "isRecruiting": recruiting, "recruitmentStartAt": "2026-01-01T00:00:00Z", "recruitmentEndAt": "2098-12-31T14:59:00Z",
            "dateLabel": "\(day) \(start)~\(end)", "location": "서울", "cost": "무료", "audience": "누구나", "qualification": NSNull(),
            "roles": ["개발자"],
            "schedules": [["id": id, "title": title, "startAt": "\(day)T\(start):00+09:00", "endAt": "\(day)T\(end):00+09:00",
                           "dateLabel": "\(day) \(start)~\(end)", "timeZone": "Asia/Seoul"]],
            "officialUrl": "https://official.example.test/\(id)", "applicationUrl": application ?? NSNull(),
            "sourceCheckedAt": "2026-01-01T00:00:00.000Z", "validUntil": "2099-01-01T00:00:00Z",
            "freshness": freshness, "sourceNote": "테스트 출처"
        ]
    }
    static var body: [String: Any] { [
        "generatedAt": "2026-01-01T00:00:00Z",
        "organizations": [["id": "a1000000-0000-4000-8000-000000000001", "name": "테스트 주최", "description": ""]],
        "programs": [],
        "activities": [
            activity(conference, "테스트 컨퍼런스", day: "2026-10-24", start: "13:00", end: "17:00"),
            activity(camp, "테스트 메이커 캠프", type: "selection", day: "2026-11-07", start: "10:00", end: "18:00"),
            activity(scheduled, "테스트 예정 밋업", status: "scheduled", recruiting: false, day: "2026-11-21", start: "14:00", end: "17:00"),
            activity(stale, "테스트 오래된 활동", freshness: "stale", day: "2026-11-28", start: "14:00", end: "17:00")
        ]
    ] }
    static var json: Data { try! JSONSerialization.data(withJSONObject: body) }
}
