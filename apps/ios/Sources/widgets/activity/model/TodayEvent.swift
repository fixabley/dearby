import Foundation

/// 오늘 내 행사 (contract "오늘 내 행사 카드" 2026-10-26): the one applied activity the top of 발견 speaks for — a 활동 일정 block of it is today, or
/// starts within 7 days. Built from what the app already holds (`ActivityViewModel.appliedProgress`, `SessionFavoritesModel.items`); no request.
struct TodayEvent: Equatable {
    enum Timing: Equatable {
        case today
        /// Whole Seoul days until the block's first day (1...7).
        case upcoming(days: Int)
    }
    let activity: ActivityModel
    let timing: Timing
    /// The block the card shows and opens: today, the next favorited session's block, else the block still running or next up (the day's
    /// last one once all are over); upcoming, the nearest block ahead.
    let block: ActivityStageModel
    /// Today only: the first favorited session of this activity today that has not ended; nil without one.
    let session: FavoriteSession?
    /// Other applied activities that also qualify (`외 n개`).
    var others = 0

    /// `오늘` or `D-2`.
    var badge: String {
        switch timing {
        case .today: "오늘"
        case .upcoming(let days): "D-\(days)"
        }
    }
    /// `오늘 · 우아콘 2026` / `D-2 · 우아콘 2026`: how the card reads.
    var title: String { badge + " · " + activity.title }
    /// The body: today, the next session (`14:50 · 트랙 B · 그랜드볼룸 B · 세션 제목`, `지금 · …` while it runs) or the block
    /// (`오후 세션 13:50–17:30`); upcoming, the block's title and its date and time (`오전 세션 · 10월 28일 (수) 10:50 ~ 12:30`).
    func detail(now: Date) -> String {
        switch timing {
        case .today:
            if let session {
                let when = session.session.start <= now ? "지금" : ActivityText.time(session.session.start, zone: session.zone)
                return ([when, session.track?.name, session.track?.room, session.session.title].compactMap { $0 }).joined(separator: " · ")
            }
            guard !block.allDay, let start = block.start else { return blockTitle + " · 종일" }
            return blockTitle + " " + ActivityText.time(start, zone: block.timeZone) + (block.end.map { "–" + ActivityText.time($0, zone: block.timeZone) } ?? "")
        case .upcoming:
            return [blockTitle, block.text(now: now)].compactMap { $0 }.joined(separator: " · ")
        }
    }
    /// The block's title, `활동` for the untitled activity period (as the progress track names it).
    var blockTitle: String { block.title.isEmpty ? "활동" : block.title }

    /// Picks the card's activity from the applied ones (dropped: no track, ended, 탈락). A 활동 일정 block is a stage of the 활동 일정 section
    /// (`ActivityStage.inActivitySection`: `phase: ACTIVITY`, the activity period, any timetable block) with a start and spanning at most 7
    /// Seoul days — a months-long all-day period is not an event day and would pin the card for the whole period. Today's activities come
    /// first (earliest block), then the nearest upcoming; `others` counts the rest.
    static func pick(_ applied: [AppliedActivity], favorites: [FavoriteSession], now: Date) -> TodayEvent? {
        let seoul = seoul
        let today = seoul.startOfDay(for: now)
        var found: [(event: TodayEvent, rank: Int, start: Date)] = []
        for item in applied {
            guard let progress = item.progress, !progress.ended else { continue }
            let blocks = progress.stages.filter(\.inActivitySection).map(\.source).compactMap { block in days(block).map { (block: block, days: $0) } }
                .sorted { ($0.block.start ?? .distantFuture) < ($1.block.start ?? .distantFuture) }
            let todays = blocks.filter { $0.days.contains(today) }.map(\.block)
            if !todays.isEmpty {
                let mine = favorites.filter { $0.activityID == item.id && seoul.isDate($0.session.start, inSameDayAs: now) }
                    .sorted { $0.session.start < $1.session.start }
                let next = mine.first { $0.session.end > now }
                let block = next.flatMap { favorite in todays.first { $0.id == favorite.scheduleID } }
                    ?? todays.first { $0.allDay || ($0.end ?? .distantFuture) > now } ?? todays[todays.count - 1]
                found.append((TodayEvent(activity: item.activity, timing: .today, block: block, session: next), 0, block.start ?? today))
            } else if let ahead = blocks.first(where: { $0.days.lowerBound > today }), let start = ahead.block.start {
                let days = ActivityStages.days(from: now, to: start)
                guard days <= 7 else { continue }
                found.append((TodayEvent(activity: item.activity, timing: .upcoming(days: days), block: ahead.block, session: nil), 1, start))
            }
        }
        found.sort { ($0.rank, $0.start, $0.event.activity.id) < ($1.rank, $1.start, $1.event.activity.id) }
        guard var first = found.first?.event else { return nil }
        first.others = found.count - 1
        return first
    }
    /// The Seoul days a block covers, first midnight through last midnight; nil without a start, when it ends before it starts, or when it
    /// spans more than 7 days. A timed block without an end is its start day; an all-day block's `end` is exclusive.
    private static func days(_ block: ActivityStageModel) -> ClosedRange<Date>? {
        guard let start = block.start else { return nil }
        let first = seoul.startOfDay(for: start)
        let last: Date
        if block.allDay {
            last = block.end.flatMap { seoul.date(byAdding: .day, value: -1, to: $0) } ?? first
        } else {
            last = seoul.startOfDay(for: block.end ?? start)
        }
        guard last >= first, (seoul.dateComponents([.day], from: first, to: last).day ?? 0) <= 7 else { return nil }
        return first...last
    }
    private static var seoul: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = ActivityText.seoul
        return calendar
    }
}
