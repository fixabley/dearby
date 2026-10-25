import Foundation

/// 즐겨찾기 (contract "즐겨찾기(하트)·새 모집 푸시 알림" 2026-10-25): an activity or a program, as `PUT/DELETE /v1/me/favorites/{programs|activities}/{id}` names it.
enum FavoriteTarget: Hashable, Sendable {
    case program(Int64)
    case activity(String)
    var path: String {
        switch self {
        case .program(let id): "programs/\(id)"
        case .activity(let id): "activities/\(id)"
        }
    }
}

/// 알림 수준 (contract "프로그램·활동별 알림 수준" 2026-10-25): `user_favorites.notify_level`; a new favorite starts at `신청일만`.
enum NotifyLevel: String, CaseIterable, Identifiable, Sendable {
    case all = "ALL", application = "APPLICATION", none = "NONE"
    var id: Self { self }
    /// An unknown or missing value reads as the default.
    init(raw: String?) { self = raw.flatMap(NotifyLevel.init(rawValue:)) ?? .application }
    var title: String {
        switch self {
        case .all: "전체"
        case .application: "신청일만"
        case .none: "없음"
        }
    }
    var detail: String {
        switch self {
        case .all: "모든 활동 일정과 새 모집"
        case .application: "모집 시작·모집 마감"
        case .none: "알림을 받지 않아요"
        }
    }
    /// iOS `bell.fill` / `bell` / `bell.slash` (contract).
    var symbol: String {
        switch self {
        case .all: "bell.fill"
        case .application: "bell"
        case .none: "bell.slash"
        }
    }
}

/// One favorite program from `GET /v1/me/favorites`; `newRecruitment` until the user opens it from the favorites screen.
struct FavoriteProgram: Identifiable, Equatable, Sendable {
    let program: ProgramSummary
    var newRecruitment: Bool
    var level: NotifyLevel
    var id: Int64 { program.id }
}

/// One favorite activity from `GET /v1/me/favorites` (deadline order, past ones last) with its program's name.
struct FavoriteActivity: Identifiable, Equatable, Sendable {
    let activity: ActivityModel
    let programName: String?
    var level: NotifyLevel
    var id: String { activity.id }
}

struct FavoriteList: Sendable {
    let programs: [FavoriteProgram]
    let activities: [FavoriteActivity]
}
