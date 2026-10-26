import Foundation

/// 활동 상태 (contract "활동 상태" 2026-10-26): the server's `Activity.status`, judged in Seoul time. The app shows it and never judges it,
/// except for an older response without one (`ActivityModel.status(at:)`).
enum ActivityStatus: String, CaseIterable, Sendable {
    case recruitingUpcoming = "RECRUITING_UPCOMING", recruiting = "RECRUITING", recruitmentClosed = "RECRUITMENT_CLOSED"
    case noRecruitment = "NO_RECRUITMENT", inProgress = "IN_PROGRESS", ended = "ENDED"
    /// The badge text; nil for 모집 정보 없음, which shows no badge and reads `모집 일정 미정` in the date's place.
    var badge: String? {
        switch self {
        case .recruitingUpcoming: "모집 예정"
        case .recruiting: "모집 중"
        case .recruitmentClosed: "모집 마감"
        case .noRecruitment: nil
        case .inProgress: "진행 중"
        case .ended: "종료"
        }
    }
    /// Applying is still ahead or open: the recruitment row stands out and the detail CTA arms the "신청하셨나요?" prompt.
    var applicable: Bool { self == .recruiting || self == .recruitingUpcoming }
}

/// 발견 상태 필터의 세 묶음 (contract "활동 상태" 앱·웹 UI, regrouped 2026-10-26 by the activity's own schedule): the server takes statuses,
/// the app sends a group's statuses together. `지원 가능` is applying ahead or open; `진행·예정` is every activity that has not ended (running,
/// recruitment closed before it starts, or no recruitment schedule yet — the server judges `ENDED` first, so these are all unfinished);
/// `지난 활동` is `ENDED` alone. The raw value is the stored value and the 사용 통계 `groups` value (`in_progress` kept for stored choices).
enum StatusGroup: String, CaseIterable, Sendable {
    case available, inProgress = "in_progress", past
    var title: String {
        switch self {
        case .available: "지원 가능"
        case .inProgress: "진행·예정"
        case .past: "지난 활동"
        }
    }
    var statuses: [ActivityStatus] {
        switch self {
        case .available: [.recruitingUpcoming, .recruiting]
        case .inProgress: [.inProgress, .recruitmentClosed, .noRecruitment]
        case .past: [.ended]
        }
    }
    init(_ status: ActivityStatus) {
        self = StatusGroup.allCases.first { $0.statuses.contains(status) } ?? .past
    }
}
