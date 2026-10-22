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
    var mark: StageMark
    var state: State
    /// The marked status as the schedule names it (대기·통과·탈락 or the admin's names).
    var status: ScheduleStatus { mark.status(in: source.statuses) }
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
    /// Every selection stage before the activity period is marked PASSED (contract: 참여 확정 is inferred from statuses, 2026-10-22).
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
    private static var seoul: Calendar {
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
                                        mark: marks[application.id] ?? .wait, state: .upcoming))
        }
        let ordered = activity.stages.enumerated()
            .sorted { ($0.element.anchor ?? .distantFuture, $0.offset) < ($1.element.anchor ?? .distantFuture, $1.offset) }.map(\.element)
        for source in ordered {
            stages.append(stage(source, name: source.title, badgeName: badgeName(source), icon: icon(source.kind), marks: marks))
        }
        let selectionCount = stages.count
        if let period = activity.activityStage {
            let name = period.title.isEmpty ? "활동" : period.title
            stages.append(stage(period, name: name, badgeName: name, icon: "flag", marks: marks))
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
        // 참여 중: an activity period follows selection stages that are all marked passed.
        let participating = !failed && selectionCount > 0 && selectionCount < stages.count
            && stages.prefix(selectionCount).allSatisfy { $0.mark.kind == .passed }
        return ActivityProgress(stages: stages, currentIndex: failed ? nil : currentIndex, failed: failed, participating: participating)
    }
    private static func stage(_ source: ActivityStageModel, name: String, badgeName: String, icon: String, marks: [String: StageMark]) -> ActivityStage {
        ActivityStage(id: source.id, name: name, badgeName: badgeName, icon: icon, source: source, mark: marks[source.id] ?? .wait, state: .upcoming)
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
    /// `캘린더에 추가` (contract "내 캘린더에 추가" 2026-10-22): "활동명 · 일정 제목" (the `지원` stage reads `모집 마감`), a timed stage at its
    /// times (a lone bound as a point in time), a date-only stage as all-day through its last Seoul day, the place and the official link.
    /// nil for a date-less stage.
    func calendarDraft(_ activity: ActivityModel) -> CalendarEventDraft? {
        guard let anchor = source.start ?? source.end else { return nil }
        var seoul = Calendar(identifier: .gregorian)
        seoul.timeZone = ActivityText.seoul
        let start: Date, end: Date
        if source.allDay {
            start = source.start ?? seoul.date(byAdding: .day, value: -1, to: anchor) ?? anchor
            end = (source.end ?? seoul.date(byAdding: .day, value: 1, to: start) ?? start).addingTimeInterval(-1)
        } else {
            start = anchor
            end = max(source.end ?? anchor, anchor)
        }
        let location = activity.place.map { "\($0.name), \($0.address)" } ?? activity.location
        return CalendarEventDraft(title: "\(activity.title) · \(id == ActivityStages.applicationStageID ? "모집 마감" : name)", start: start,
                                  end: end, allDay: source.allDay, timeZone: source.allDay ? ActivityText.seoul : TimeZone(identifier: source.timeZone),
                                  location: location, url: activity.officialURL)
    }
}
