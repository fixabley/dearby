import Foundation

/// The user's own mark on a stage (contract "내 활동 진행 트랙", statuses per schedule since 2026-10-22): an admin status by id, or a
/// default one (no id) by kind; unmarked reads as `wait`. Not an organizer result.
struct StageMark: Hashable, Sendable {
    let statusID: Int64?
    let kind: ScheduleStatus.Kind
    static let wait = StageMark(statusID: nil, kind: .waiting)
    init(statusID: Int64?, kind: ScheduleStatus.Kind) {
        self.statusID = statusID
        self.kind = kind
    }
    init(_ status: ScheduleStatus) { self.init(statusID: status.id, kind: status.kind) }
    /// The option this mark stands for: the same id, else the first of the same kind (a default mark on a schedule the admin
    /// later gave its own statuses), else the first option.
    func status(in options: [ScheduleStatus]) -> ScheduleStatus {
        options.first { statusID != nil && $0.id == statusID } ?? options.first { $0.kind == kind }
            ?? options.first ?? ScheduleStatus(id: nil, name: "대기", kind: .waiting)
    }
}

/// One stage after the time judgment and the user's mark were applied.
struct ActivityStage: Identifiable, Equatable {
    enum State: Equatable { case past, current, upcoming }
    let id: String
    let name: String
    /// Badge suffix: the kind label for 마감·발표·면접, the title for events and `지원`.
    let badgeName: String
    let icon: String
    /// The schedule behind the stage; for `지원` only the application window's end (README: 날짜는 모집 마감). Its `id` is the
    /// schedule id the marks are keyed by and the server stores them under.
    let source: ActivityStageModel
    /// A 선발 과정 stage (contract "선발 과정·활동 일정 분리" 2026-10-26): only these take 통과·대기·탈락; an 활동 일정 stage keeps the date
    /// and 예정/지남 by time, and its stored mark is not read (`.wait`).
    let selection: Bool
    /// The `활동 일정` section holds the activity stages and, whatever its phase, any block with a 트랙·세션 시간표 (contract: 시간표 블록은
    /// `phase`와 상관없이 `활동 일정`); the rest is `선발 과정`.
    var inActivitySection: Bool { !selection || source.hasTimetable }
    var mark: StageMark
    var state: State
    /// The marked status as the schedule names it (대기·통과·탈락 or the admin's names).
    var status: ScheduleStatus { mark.status(in: source.statuses) }
    /// The schedule's 통과 option; nil when the admin's statuses have no `PASSED` kind.
    var passedOption: ScheduleStatus? { source.statuses.first { $0.kind == .passed } }
    /// The day the stage counts from: its start, or its end when only that is known.
    var anchor: Date? { source.anchor }
    /// For the overlap check (timed, or date-only with an admin time window); empty when the stage cannot be compared.
    var overlapSchedules: [ActivityScheduleModel] { source.overlapSchedules }
    /// Structured dates, else the collected label (contract "일정 표시 우선순위"); `날짜 미확인` with neither. `지원` is the recruitment deadline (`~ … 마감`).
    func dateText(now: Date) -> String { source.text(now: now, recruitment: id == ActivityStages.applicationStageID) ?? "날짜 미확인" }
}

/// An applied activity's track: stages in order, the current one, and whether it is over.
struct ActivityProgress: Equatable {
    let stages: [ActivityStage]
    /// nil when every stage is past or the user marked a 탈락: the activity belongs to `끝난 활동`.
    let currentIndex: Int?
    let failed: Bool
    /// Every 선발 단계 is marked PASSED and a later stage remains (contract: 참여 확정 is inferred from statuses, 2026-10-22).
    var participating = false
    var ended: Bool { currentIndex == nil }
    var current: ActivityStage? { currentIndex.map { stages[$0] } }
    /// List order: the current stage's start (its end when only that is known); nil (date-less or ended) sorts last.
    var nextStart: Date? { current?.anchor }
    /// `D-3 · 면접`; `D-DAY · 발표` while the current stage is ongoing; `날짜 미확인 · 발표` without a date.
    /// Ended tracks read `지남` (every stage over) or `탈락` (the user's mark, shown in the danger colour); a track whose selection
    /// stages were all passed reads `참여 중` until the activity period ends.
    func badge(now: Date) -> String {
        guard let current else { return failed ? "탈락" : "지남" }
        if participating { return "참여 중" }
        guard let start = current.anchor else { return "날짜 미확인 · \(current.badgeName)" }
        let days = ActivityStages.days(from: now, to: start)
        return "\(days > 0 ? "D-\(days)" : "D-DAY") · \(current.badgeName)"
    }
    /// The whole track as one element: "5단계 중 3단계 1차 면접 진행 중".
    var accessibilityLabel: String {
        guard let currentIndex, let current else { return failed ? "\(stages.count)단계 중 탈락 표시" : "\(stages.count)단계 모두 지남" }
        if participating { return "선발 단계 모두 통과, 참여 중" }
        return "\(stages.count)단계 중 \(currentIndex + 1)단계 \(current.name) 진행 중"
    }
    /// 지난 선발 단계 통과 제안 (contract "지난 선발 단계 통과 제안" 2026-10-26): the 선발 단계 in track order up to the first dated one that is
    /// not yet past — the past dated ones and the date-less ones between them; a date-less stage behind a future one is left out. "Past"
    /// is the instant (end, or start with no end; an all-day stage's `end` is the Seoul midnight that closes it), not the track's Seoul-day
    /// rule. Only unmarked stages with a 통과 option; empty when no dated stage is past.
    func autopassCandidates(now: Date) -> [ActivityStage] {
        var candidates: [ActivityStage] = []
        var pastDated = false
        for stage in stages where stage.selection {
            if let bound = stage.source.end ?? stage.source.start {
                guard stage.source.allDay ? bound <= now : bound < now else { break }
                pastDated = true
            }
            if stage.mark == .wait, stage.passedOption != nil { candidates.append(stage) }
        }
        return pastDated ? candidates : []
    }
}

/// 지난 선발 단계 통과 제안 (contract "지난 선발 단계 통과 제안" 2026-10-26): asked once per activity right after it joins 내 활동.
struct AutopassSuggestion: Identifiable, Equatable {
    let activity: ActivityModel
    /// The stages `통과로 표시` marks (`ActivityProgress.autopassCandidates`).
    let stages: [ActivityStage]
    /// The 선발 단계 the suggestion leaves alone (not past, not passed): empty means the track reads 참여 중 at once.
    let remaining: [ActivityStage]
    var id: String { activity.id }
    /// `{n}개 단계(지원, 입장권 추첨 결과 발송)가 이미 지났어요. …` with the track's stage names.
    var message: String {
        let names = stages.map(\.name).joined(separator: ", ")
        let outcome = remaining.isEmpty ? "통과로 표시하면 바로 참여 중으로 보여요."
            : "통과로 표시해요. 남은 단계(\(remaining.map(\.name).joined(separator: ", ")))는 그대로 둬요."
        return "\(stages.count)개 단계(\(names))가 이미 지났어요. \(outcome)"
    }
}

/// One applied activity with its track; `progress` is nil for a stage-less activity, which keeps the plain row.
struct AppliedActivity: Identifiable, Equatable {
    let activity: ActivityModel
    let progress: ActivityProgress?
    var id: String { activity.id }
}

/// Builds the track from an activity's application window and `schedules[]` (contract "내 활동 진행 트랙 — 2026-10-18").
enum ActivityStages {
    static let applicationStageID = "application"
    static var seoul: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Seoul") ?? .current
        return calendar
    }
    /// `지원` (application) first, `schedules[]` sorted by start with date-less ones after in API order, the activity period last.
    /// nil only when the activity has none of these. The current stage is the first one neither past by Seoul day nor marked 통과;
    /// the track ends when every stage is over (the activity period included) or a 탈락 is marked anywhere.
    /// `marks` are keyed by schedule id (`ActivityStageModel.id`).
    static func build(_ activity: ActivityModel, marks: [String: StageMark], now: Date) -> ActivityProgress? {
        var stages: [ActivityStage] = []
        if let application = activity.applicationStage {
            // The stage's date is the deadline alone (`~ … 마감`), timed or by Seoul day; its text falls back to the window's label.
            let deadline = ActivityStageModel(id: application.id, title: "지원", kind: .deadline, start: nil, end: application.end,
                                              allDay: application.allDay, dateLabel: application.dateLabel, timeZone: application.timeZone,
                                              statuses: application.statuses)
            stages.append(ActivityStage(id: applicationStageID, name: "지원", badgeName: "지원", icon: "paperplane", source: deadline,
                                        selection: true, mark: marks[application.id] ?? .wait, state: .upcoming))
        }
        let ordered = activity.stages.enumerated()
            .sorted { ($0.element.anchor ?? .distantFuture, $0.offset) < ($1.element.anchor ?? .distantFuture, $1.offset) }.map(\.element)
        for source in ordered {
            stages.append(stage(source, name: source.title, badgeName: badgeName(source), icon: icon(source.kind), selection: isSelection(source),
                                marks: marks))
        }
        if let period = activity.activityStage {
            let name = period.title.isEmpty ? "활동" : period.title
            stages.append(stage(period, name: name, badgeName: name, icon: "flag", selection: false, marks: marks))
        }
        guard !stages.isEmpty else { return nil }
        let today = seoul.startOfDay(for: now)
        var currentIndex: Int?
        for index in stages.indices {
            let done = stages[index].mark.kind == .passed || finished(stages[index], today: today)
            if currentIndex == nil {
                if done { stages[index].state = .past } else { currentIndex = index; stages[index].state = .current }
            }
        }
        let failed = stages.contains { $0.mark.kind == .failed }
        // 참여 중: every 선발 단계 is marked passed and some other stage (the activity period at least) remains.
        let selections = stages.filter(\.selection)
        let participating = !failed && !selections.isEmpty && selections.count < stages.count
            && selections.allSatisfy { $0.mark.kind == .passed }
        return ActivityProgress(stages: stages, currentIndex: failed ? nil : currentIndex, failed: failed, participating: participating)
    }
    private static func stage(_ source: ActivityStageModel, name: String, badgeName: String, icon: String, selection: Bool,
                              marks: [String: StageMark]) -> ActivityStage {
        ActivityStage(id: source.id, name: name, badgeName: badgeName, icon: icon, source: source, selection: selection,
                      mark: selection ? marks[source.id] ?? .wait : .wait, state: .upcoming)
    }
    /// 선발 과정 (contract "선발 과정·활동 일정 분리" 2026-10-26): the server's `phase`. An older server sends none, and then the superseded
    /// "통과 여부는 선발 단계에만" rule decides: 마감·발표·면접 (`DEADLINE`·`ANNOUNCEMENT`·`INTERVIEW`), and an `EVENT` whose statuses the admin
    /// set (an id: e.g. 코딩테스트, which collection files as EVENT like any 행사). The `지원` stage always is; the activity period never.
    private static func isSelection(_ source: ActivityStageModel) -> Bool {
        source.phase.map { $0 == .selection } ?? (source.kind != .event || source.statuses.contains { $0.id != nil })
    }
    /// Timed: over once its end (its start, with no end) fell before today. All-day: over once the exclusive end reached today.
    /// Date-less: never by time.
    private static func finished(_ stage: ActivityStage, today: Date) -> Bool {
        guard let end = stage.source.end ?? stage.source.start else { return false }
        return stage.source.allDay ? end <= today : end < today
    }
    /// Whole Seoul days from today to `date`'s day; negative when it passed.
    static func days(from now: Date, to date: Date) -> Int {
        let calendar = seoul
        return calendar.dateComponents([.day], from: calendar.startOfDay(for: now), to: calendar.startOfDay(for: date)).day ?? 0
    }
    private static func badgeName(_ stage: ActivityStageModel) -> String {
        switch stage.kind {
        case .deadline: "마감"
        case .announcement: "발표"
        case .interview: "면접"
        case .event: stage.title
        }
    }
    static func icon(_ kind: ActivityStageModel.Kind) -> String {
        switch kind {
        case .deadline: "flag.checkered"
        case .announcement: "megaphone"
        case .interview: "person.2"
        case .event: "calendar"
        }
    }
}

extension ActivityStage {
    /// `캘린더에 추가` (contract "내 캘린더에 추가", "반복 일정" 2026-10-22): "활동명 · 일정 제목" (the `지원` stage reads `모집 마감`), a timed
    /// stage at its times (a lone bound as a point in time), a date-only stage as all-day through its last Seoul day, the place, and the
    /// activity link (`link`) plus the official link. A repeating stage is its first occurrence plus the rule: a date-only one at its
    /// `overlapCheck` time on the first day (all-day on that day without one). nil for a date-less stage.
    func calendarDraft(_ activity: ActivityModel, link: URL) -> CalendarEventDraft? {
        guard let anchor = source.start ?? source.end else { return nil }
        var seoul = Calendar(identifier: .gregorian)
        seoul.timeZone = ActivityText.seoul
        func draft(_ start: Date, _ end: Date, allDay: Bool) -> CalendarEventDraft {
            CalendarEventDraft(title: "\(activity.title) · \(id == ActivityStages.applicationStageID ? "모집 마감" : name)", start: start,
                               end: end, allDay: allDay, timeZone: allDay ? ActivityText.seoul : TimeZone(identifier: source.timeZone),
                               location: activity.place.map { "\($0.name), \($0.address)" } ?? activity.location,
                               url: link, officialURL: activity.officialURL, recurrence: source.recurrence)
        }
        if !source.allDay { return draft(anchor, max(source.end ?? anchor, anchor), allDay: false) }
        let first = source.start ?? seoul.date(byAdding: .day, value: -1, to: anchor) ?? anchor
        if source.recurrence != nil, let window = source.overlapCheck?.interval(on: first) { return draft(window.start, window.end, allDay: false) }
        let last = source.recurrence == nil ? source.end : nil
        return draft(first, (last ?? seoul.date(byAdding: .day, value: 1, to: first) ?? first).addingTimeInterval(-1), allDay: true)
    }
}
