import ActivityKit
import Foundation

/// 행사 당일 진행 중 표시 (contract "행사 당일 진행 중 표시 — 2026-10-26 사용자 결정"): what the Live Activity carries. Compiled into the app
/// (`LiveSessionModel` starts, updates and ends it) and into `DearbyWidgets` (which draws it), so only ActivityKit and Foundation here.
struct LiveSessionAttributes: ActivityAttributes, Hashable {
    struct ContentState: Codable, Hashable {
        /// One favorited session of the day: `지금 · 트랙 B · 그랜드볼룸 B · 세션 제목` (`HH:mm` before it starts).
        struct Entry: Codable, Hashable {
            let scheduleID: String
            let title: String
            let track: String?
            let room: String?
            let start: Date
            let end: Date
            /// The track's `position` (its color, `DearbyStyle.palette`); nil for a full-width session.
            var trackPosition: Int?
        }
        /// The session running, or the next one before it starts.
        let current: Entry
        /// The one after `current`; nil during the last.
        let next: Entry?
        /// `current` has started (세션 중); else the gap before it (이동, contract "표시 디자인 — 2026-10-27").
        let running: Bool
        /// `current`'s place among the day's favorited sessions (0-based) and their count: the 진행 트랙 segments.
        let position: Int
        let total: Int
    }
    let activityID: String
    let activityTitle: String
    /// The block's time zone for the session times.
    let zone: String

    /// A tap opens that block's timetable sheet on 내 시간표 (`IncomingLink.timetable`).
    static func link(activityID: String, scheduleID: String) -> URL? { URL(string: "dearby://timetable/\(activityID)/\(scheduleID)") }
}
