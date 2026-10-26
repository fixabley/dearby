import Foundation

/// One Seoul day of 활동 일정 with two or more timed blocks (contract "하루 시간표" 2026-10-26): the detail and 진행 단계 fold the blocks under one
/// date row, and `시간표 보기` shows the day's blocks as one table (`DayTimetable`). Blocks whose start and end fall on different Seoul days, all-day
/// and date-less blocks and the activity period are never grouped.
struct ActivityDay: Identifiable, Equatable {
    /// `day-yyyyMMdd` (Seoul).
    let id: String
    /// The blocks in start order.
    let stages: [ActivityStage]
    /// `10월 28일 (수)`; the year first when outside `now`'s Seoul year.
    let title: String
    /// Earliest start and latest end of the day's blocks.
    let start: Date
    let end: Date
    /// The first block's zone: the day's times are shown in it.
    var timeZone: String { stages[0].source.timeZone }
    var hasTimetable: Bool { stages.contains { $0.source.hasTimetable } }
    /// `10:00 ~ 17:30 · 블록 4개`.
    var summary: String {
        ActivityText.time(start, zone: timeZone) + " ~ " + ActivityText.time(end, zone: timeZone) + " · 블록 \(stages.count)개"
    }
    /// `캘린더에 추가` for the whole day: `{활동 이름} · {날짜}` over the day's range, as a block's draft otherwise (`ActivityStage.calendarDraft`).
    func calendarDraft(_ activity: ActivityModel, link: URL) -> CalendarEventDraft {
        CalendarEventDraft(title: "\(activity.title) · \(title)", start: start, end: end, allDay: false, timeZone: TimeZone(identifier: timeZone),
                           location: activity.place.map { "\($0.name), \($0.address)" } ?? activity.location, url: link, officialURL: activity.officialURL)
    }
}

/// A 활동 일정 row: a block of its own, or a day folding several.
enum ActivityDayEntry: Identifiable, Equatable {
    case stage(ActivityStage)
    case day(ActivityDay)
    var id: String {
        switch self {
        case .stage(let stage): stage.id
        case .day(let day): day.id
        }
    }
}

extension ActivityStages {
    /// The 활동 일정 rows with the same-day blocks folded (contract "하루 시간표"): `stages` in track order, `periodID` the activity period's
    /// schedule id (never grouped). A day with one block stays a plain row. A day sits where its first block was.
    static func fold(_ stages: [ActivityStage], periodID: String?, now: Date) -> [ActivityDayEntry] {
        let calendar = seoulCalendar
        /// `day-yyyyMMdd` for a timed block within one Seoul day (not the activity period); nil for a block that stays its own row.
        func key(_ stage: ActivityStage) -> String? {
            let source = stage.source
            guard stage.id != periodID, !source.allDay, let start = source.start, let end = source.end, end > start,
                  calendar.isDate(start, inSameDayAs: end) else { return nil }
            return "day-" + ActivityText.format(start, "yyyyMMdd", zone: ActivityText.seoul)
        }
        var days: [String: [ActivityStage]] = [:]
        for stage in stages { if let key = key(stage) { days[key, default: []].append(stage) } }
        let thisYear = calendar.component(.year, from: now)
        var emitted: Set<String> = []
        return stages.compactMap { stage in
            guard let key = key(stage), let grouped = days[key], grouped.count > 1 else { return .stage(stage) }
            guard emitted.insert(key).inserted else { return nil }
            let sorted = grouped.sorted { ($0.source.start ?? .distantPast, $0.id) < ($1.source.start ?? .distantPast, $1.id) }
            let start = sorted.compactMap(\.source.start).min() ?? .distantPast
            let pattern = calendar.component(.year, from: start) == thisYear ? "M월 d일 (E)" : "yyyy년 M월 d일 (E)"
            return .day(ActivityDay(id: key, stages: sorted, title: ActivityText.format(start, pattern, zone: ActivityText.seoul), start: start,
                                    end: sorted.compactMap(\.source.end).max() ?? start))
        }
    }
    /// The day `blockID` shares with other blocks of `activity`'s 활동 일정, for the block sheet's `이 날 전체 보기`; nil when it is alone that day.
    static func day(of blockID: String, in activity: ActivityModel, now: Date) -> ActivityDay? {
        let stages = (build(activity, marks: [:], now: now)?.stages ?? []).filter(\.inActivitySection)
        for case .day(let day) in fold(stages, periodID: activity.activityStage?.id, now: now) where day.stages.contains(where: { $0.id == blockID }) {
            return day
        }
        return nil
    }
    private static var seoulCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = ActivityText.seoul
        return calendar
    }
}

/// 하루 시간표 (contract "하루 시간표" 2026-10-26): the day's blocks as one `Timetable`. Tracks are joined by name across the blocks (the same name is
/// the same column; `position` is the merged order, so the colors follow the day table's own order); a block without a timetable, whose timetable
/// the server no longer has, or that could not be loaded is a full-width placeholder row with the block's title (`placeholder`: no heart).
enum DayTimetable {
    enum Outcome: Equatable {
        case loaded(Timetable)
        /// `hasTimetable` but the server answered 404, or the block never had one.
        case absent
        case failed
    }
    /// nil when no block's timetable loaded (the sheet shows 다시 시도 when any failed, 시간표가 없어요 otherwise).
    static func merge(activityID: String, title: String, blocks: [(block: ActivityStageModel, outcome: Outcome)]) -> Timetable? {
        guard blocks.contains(where: { if case .loaded = $0.outcome { true } else { false } }) else { return nil }
        let zone = blocks.first?.block.timeZone ?? "Asia/Seoul"
        var tracks: [TimetableTrack] = []
        var canonical: [Int64: Int64] = [:]
        for case (_, .loaded(let timetable)) in blocks {
            for track in timetable.tracks.sorted(by: { ($0.position, $0.id) < ($1.position, $1.id) }) {
                let name = track.name.trimmingCharacters(in: .whitespaces)
                if let existing = tracks.first(where: { $0.name == name }) {
                    canonical[track.id] = existing.id
                } else {
                    canonical[track.id] = track.id
                    tracks.append(TimetableTrack(id: track.id, name: name, room: track.room, position: tracks.count))
                }
            }
        }
        var sessions: [TimetableSession] = []
        for (index, (block, outcome)) in blocks.enumerated() {
            switch outcome {
            case .loaded(let timetable):
                sessions += timetable.sessions.map { session in
                    TimetableSession(id: session.id, trackID: session.trackID.flatMap { canonical[$0] }, kind: session.kind, title: session.title,
                                     description: session.description, start: session.start, end: session.end, speakers: session.speakers,
                                     tags: session.tags)
                }
            case .absent, .failed:
                guard let start = block.start, let end = block.end, start < end else { continue }
                let title = block.title.isEmpty ? "활동" : block.title
                sessions.append(TimetableSession(id: Int64(-(index + 1)), trackID: nil, kind: placeholderKind(title), title: title,
                                                 description: nil, start: start, end: end, speakers: [], tags: [],
                                                 placeholder: outcome == .failed ? .failed : .block))
            }
        }
        return Timetable(activityID: activityID, scheduleID: "", scheduleTitle: title, zone: zone, tracks: tracks,
                         sessions: sessions.sorted { ($0.start, $0.end, $0.id) < ($1.start, $1.end, $1.id) })
    }
    /// A block titled as a break or a meal takes that band's look; anything else is a plain placeholder.
    private static func placeholderKind(_ title: String) -> TimetableSession.Kind {
        let lower = title.lowercased()
        if ["쉬는", "휴식", "브레이크", "break"].contains(where: lower.contains) { return .breakTime }
        if ["점심", "저녁", "식사", "lunch", "dinner"].contains(where: lower.contains) { return .meal }
        return .other
    }
}
