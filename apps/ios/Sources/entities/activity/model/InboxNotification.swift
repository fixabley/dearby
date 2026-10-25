import Foundation

/// 알림 목록 (contract "알림 목록(발견 우상단 종)" 2026-10-25): one program's notifications, newest group first.
struct InboxGroup: Identifiable, Equatable, Sendable {
    let program: ProgramSummary
    var unread: Int
    var items: [InboxItem]
    var id: Int64 { program.id }
    /// The newest notification's time; the `이번 주`/`지난 알림` split and the card order read it.
    var latest: Date? { items.compactMap(\.createdAt).max() }
}

/// One notification: what happened to which activity, when it arrived and whether it was read.
struct InboxItem: Identifiable, Equatable, Sendable {
    enum Kind: String, Sendable {
        case new = "NEW", applicationStart = "APPLICATION_START", deadlineEve = "DEADLINE_EVE", deadline = "DEADLINE"
        case announcement = "ANNOUNCEMENT", interview = "INTERVIEW", event = "EVENT", activityStart = "ACTIVITY_START"
        var label: String {
            switch self {
            case .new: "새 모집"
            case .applicationStart: "모집 시작"
            case .deadlineEve: "마감 D-1"
            case .deadline: "오늘 마감"
            case .announcement: "발표"
            case .interview: "면접"
            case .event: "행사"
            case .activityStart: "활동 시작"
            }
        }
    }
    let id: Int64
    /// Nil for a kind this build does not know; the row then says `알림`.
    let kind: Kind?
    let activityID: String
    let activityName: String
    let createdAt: Date?
    var read: Bool
}

/// One page of `GET /v1/me/notifications`; a nil `nextCursor` is the last page.
struct InboxPage: Sendable {
    let groups: [InboxGroup]
    let nextCursor: String?
}
