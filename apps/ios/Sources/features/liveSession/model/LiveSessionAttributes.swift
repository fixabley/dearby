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
        /// Whole minutes, rounded up (at least 1), to `current`'s end while running, else to its start: 이동 shows only these (`12분`), and the
        /// compact island while an hour or more is left. Refreshed at each minute boundary (`LiveSessionModel.moment`).
        var minutes = 1
    }
    let activityID: String
    let activityTitle: String
    /// The block's time zone for the session times.
    let zone: String

    /// 행사 안내 (`LiveSessionModel.Guide`: off·on·dismissed) in the app's `UserDefaults`; `EndLiveGuideIntent` turns it off from the activity.
    static let guideKey = "dearby.liveSession.guide"

    /// A tap opens that block's timetable sheet on 내 시간표 (`IncomingLink.timetable`).
    static func link(activityID: String, scheduleID: String) -> URL? { URL(string: "dearby://timetable/\(activityID)/\(scheduleID)") }
}
