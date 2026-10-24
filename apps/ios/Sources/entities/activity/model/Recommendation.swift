import Foundation

/// One 추천 item (contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24): an open activity and the short reasons the server gives
/// (`데이터·AI`, `부트캠프`, `11월 7일 마감`, `지원 자격 확인 필요`). Scores are never sent.
struct RecommendedActivity: Identifiable, Sendable {
    let activity: ActivityModel
    let programName: String?
    let reasons: [String]
    /// The program beside it (contract "맞춤 대기열" 2026-10-24: `HIDE_PROGRAM` hides its other cards too) with its logo and 세로 커버
    /// (`ProgramSummary.coverUrl`), both resolved against the API origin; nil when the server sent none.
    var programID: Int64? = nil
    var logoURL: URL? = nil
    var coverURL: URL? = nil
    var id: String { activity.id }
}

/// 맞춤 대기열 반응 (contract "맞춤 대기열" 2026-10-24 `POST /v1/me/recommendation-signals`): signed in only.
enum RecommendationSignal: String, Sendable {
    case like = "LIKE", unlike = "UNLIKE", dismiss = "DISMISS", lessField = "LESS_FIELD", hideProgram = "HIDE_PROGRAM"
    case ineligible = "INELIGIBLE", complete = "COMPLETE", skipEarly = "SKIP_EARLY", openDetail = "OPEN_DETAIL"
}

/// `GET /v1/discovery/recommendations`: the items and, when the server sends it, how many activities match in all.
struct RecommendationPage: Sendable {
    let items: [RecommendedActivity]
    let total: Int?
}

/// A tag as the 추천 funnel lists it: 분야 (`isField`, grouped under its 상위 분야 `parentID`) or 성격.
struct TagOption: Identifiable, Equatable, Sendable {
    let id: Int64
    let name: String
    let isField: Bool
    let parentID: Int64?
}
