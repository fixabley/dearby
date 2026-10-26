import Foundation

/// 트랙·세션 시간표 (contract "트랙·세션 시간표" 2026-10-26): one schedule block's tracks and sessions from
/// `GET /v1/activities/:id/schedules/:scheduleId/timetable`. Session times are in the block's zone (`zone`).
struct Timetable: Equatable, Sendable {
    let activityID: String
    let scheduleID: String
    let scheduleTitle: String
    /// The block's time zone (an all-day block's is Seoul): session times are shown in it.
    let zone: String
    /// In `position` order.
    let tracks: [TimetableTrack]
    /// In start order.
    let sessions: [TimetableSession]
    func track(_ id: Int64?) -> TimetableTrack? { id.flatMap { id in tracks.first { $0.id == id } } }
}

/// A track (강연장): its `position` picks the color from the app's palette, so the same order gives the same color.
struct TimetableTrack: Equatable, Identifiable, Sendable {
    let id: Int64
    let name: String
    let room: String?
    let position: Int
}

struct TimetableSession: Equatable, Identifiable, Sendable {
    enum Kind: String, Sendable { case talk = "TALK", keynote = "KEYNOTE", breakTime = "BREAK", meal = "MEAL", other = "OTHER" }
    struct Speaker: Equatable, Sendable {
        let name: String
        let affiliation: String?
        /// `이름 · 소속`, or the name alone.
        var text: String { [name, affiliation].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ") }
    }
    let id: Int64
    /// nil: a full-width session over every track (키노트·쉬는시간·점심); every session of a timetable without tracks.
    let trackID: Int64?
    let kind: Kind
    let title: String
    let description: String?
    let start: Date
    let end: Date
    let speakers: [Speaker]
    /// 분야 tags in the server's order (at most 5).
    let tags: [Tag]
    /// A band rather than a card: 쉬는시간·점심.
    var isBreak: Bool { kind == .breakTime || kind == .meal }
    /// `a.start < b.end && b.start < a.end` (contract "찜 겹침").
    func overlaps(_ other: TimetableSession) -> Bool { start < other.end && other.start < end }
}

/// One item of `GET /v1/me/session-favorites` (내 시간표, session start order).
struct FavoriteSession: Equatable, Identifiable, Sendable {
    let activityID: String
    let activityName: String
    let scheduleID: String
    let scheduleTitle: String
    let session: TimetableSession
    /// The session's track; nil for a full-width session or on an older response.
    var track: TimetableTrack?
    /// The block's zone (an all-day block's is Seoul): the session's times and day are read in it.
    var zone = "Asia/Seoul"
    var id: Int64 { session.id }
}
