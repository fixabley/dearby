import Foundation

extension LiveSessionModel.Plan {
    /// 행사 당일 진행 중 표시 (contract "행사 당일 진행 중 표시"): the activity the 오늘 내 행사 card speaks for today (`TodayEvent.pick`, `.today`)
    /// and 내 시간표 of it on that Seoul day, in start order; nil without both. `analytics` are the activity parameters of `live_session_shown`.
    @MainActor static func today(_ applied: [AppliedActivity], favorites: [FavoriteSession], now: Date) -> Self? {
        guard let event = TodayEvent.pick(applied, favorites: favorites, now: now), event.timing == .today else { return nil }
        var seoul = Calendar(identifier: .gregorian)
        seoul.timeZone = ActivityText.seoul
        let mine = favorites.filter { $0.activityID == event.activity.id && seoul.isDate($0.session.start, inSameDayAs: now) }
            .sorted { ($0.session.start, $0.session.end, $0.id) < ($1.session.start, $1.session.end, $1.id) }
        guard let first = mine.first else { return nil }
        let entries = mine.map {
            LiveSessionAttributes.ContentState.Entry(scheduleID: $0.scheduleID, title: $0.session.title, track: $0.track?.name,
                                                     room: $0.track?.room, start: $0.session.start, end: $0.session.end)
        }
        return Self(attributes: LiveSessionAttributes(activityID: event.activity.id, activityTitle: event.activity.title, zone: first.zone),
                    entries: entries, analytics: AppAnalytics.activity(event.activity.id, event.activity).mapValues { "\($0)" })
    }
}
