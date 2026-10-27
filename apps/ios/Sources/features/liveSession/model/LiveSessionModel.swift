import ActivityKit
import BackgroundTasks
import Foundation
import Observation
import OSLog

private let log = Logger(subsystem: "com.dearby.app", category: "liveSession")

/// 행사 당일 진행 중 표시 (contract "행사 당일 진행 중 표시 — 2026-10-26 사용자 결정"): a Live Activity (잠금 화면·Dynamic Island) on an event day —
/// the activity the 오늘 내 행사 card speaks for (`TodayEvent.pick`, today) when 내 시간표 has sessions of it that day, from 30 minutes before the
/// first favorited session to the end of the last. Built on the device from what the app already holds; nothing goes to the server and the
/// activity has no push token. The app starts and updates it in the foreground; the day's plan is kept on the device so a `BGAppRefreshTask`
/// asked for each session boundary can move the display on without the account data (best effort: with the app closed the display may not
/// move on to the next session — the contract leaves that to 2단계 서버 푸시). The remaining time counts down in the widget (`Text(timerInterval:)`).
@MainActor @Observable final class LiveSessionModel {
    /// `행사 진행 중 표시` (설정 › 알림): on until turned off, kept on the device; off ends the activity.
    var enabled: Bool {
        didSet { UserDefaults.standard.set(enabled, forKey: Self.enabledKey) }
    }
    nonisolated static let enabledKey = "dearby.liveSession.enabled"
    nonisolated static let refreshTaskID = "io.wid.dearby.liveSession.refresh"
    nonisolated static let lead: TimeInterval = 30 * 60
    private nonisolated static let planKey = "dearby.liveSession.plan"
    private nonisolated static let shownDayKey = "dearby.liveSession.shownDay"
    @ObservationIgnored private var applying: Task<Void, Never>?
    @ObservationIgnored private var timer: Task<Void, Never>?

    init() {
        enabled = UserDefaults.standard.object(forKey: Self.enabledKey) as? Bool ?? true
    }

    /// The day's favorited sessions of the activity, in start order, with the 사용 통계 activity parameters of `live_session_shown`.
    struct Plan: Codable, Equatable {
        let attributes: LiveSessionAttributes
        let entries: [LiveSessionAttributes.ContentState.Entry]
        let analytics: [String: String]
    }
    /// What the activity shows at one moment: nothing (`state` nil) outside the window, and when the next change is due.
    struct Moment: Equatable {
        let state: LiveSessionAttributes.ContentState?
        let boundary: Date?
    }

    /// The account data arrived or changed (`Plan.today`, widgets layer): the plan is saved and applied; nil ends the activity.
    func sync(_ plan: Plan?, now: Date = Date()) {
        Self.save(plan)
        refresh(now: now)
    }
    /// The saved plan again: at launch, on return to the foreground, the switch, and at each session boundary while the app is open.
    func refresh(now: Date = Date()) {
        timer?.cancel()
        // One `apply` at a time: a run still going finishes first (ActivityKit calls are not cancellable), so no second request slips in.
        let previous = applying
        previous?.cancel()
        applying = Task { [weak self] in
            await previous?.value
            let boundary = await Self.apply(now: now)
            guard !Task.isCancelled else { return }
            self?.wait(until: boundary)
        }
    }
    private func wait(until date: Date?) {
        guard let date else { return }
        timer = Task { [weak self] in
            try? await Task.sleep(for: .seconds(max(1, date.timeIntervalSinceNow + 1)))
            guard !Task.isCancelled else { return }
            self?.refresh()
        }
    }

    /// The window runs from 30 minutes before the first session to the end of the last; `current` is the first session not over,
    /// `next` the first starting after it and not over. The boundary is the nearest start or end still ahead (the window start before it).
    nonisolated static func moment(_ plan: Plan, now: Date) -> Moment {
        let entries = plan.entries
        guard let first = entries.first, let last = entries.map(\.end).max(), now < last else { return Moment(state: nil, boundary: nil) }
        let opens = first.start.addingTimeInterval(-lead)
        guard now >= opens, let current = entries.first(where: { $0.end > now }) else { return Moment(state: nil, boundary: opens) }
        let next = entries.first { $0.start > current.start && $0.end > now }
        let boundary = entries.flatMap { [$0.start, $0.end] }.filter { $0 > now }.min()
        return Moment(state: .init(current: current, next: next, running: current.start <= now), boundary: boundary)
    }
    /// The gate `apply` goes through: nothing with the switch off or without a saved plan (a sign-out clears it, `sync(nil)`).
    nonisolated static func moment(enabled: Bool, plan: Plan?, now: Date) -> Moment? {
        guard enabled, let plan else { return nil }
        return moment(plan, now: now)
    }

    /// Starts, updates or ends the activity from the saved plan and the switch; asks for a background wake-up at the next boundary and
    /// returns it. Runs for the app (`refresh`) and for the background task alike; nonisolated so the activities never cross an actor.
    @discardableResult nonisolated static func apply(now: Date = Date()) async -> Date? {
        let enabled = UserDefaults.standard.object(forKey: enabledKey) as? Bool ?? true
        let plan = load()
        let current = Self.moment(enabled: enabled, plan: plan, now: now)
        let activities = Activity<LiveSessionAttributes>.activities.filter { [.active, .stale].contains($0.activityState) }
        if let plan, let state = current?.state {
            let content = ActivityContent(state: state, staleDate: current?.boundary)
            var kept = false
            for activity in activities {
                if !kept, activity.attributes == plan.attributes {
                    await activity.update(content)
                    kept = true
                } else {
                    await activity.end(nil, dismissalPolicy: .immediate)
                }
            }
            if !kept, ActivityAuthorizationInfo().areActivitiesEnabled {
                do {
                    _ = try Activity.request(attributes: plan.attributes, content: content)
                    await reportShown(plan, now: now)
                } catch {
                    log.warning("Live Activity request failed: \(String(describing: error), privacy: .public)")
                }
            }
        } else {
            for activity in activities { await activity.end(nil, dismissalPolicy: .immediate) }
        }
        if let boundary = current?.boundary {
            let request = BGAppRefreshTaskRequest(identifier: refreshTaskID)
            request.earliestBeginDate = boundary
            // Unavailable on the simulator and when Background App Refresh is off; the foreground timer still runs.
            try? BGTaskScheduler.shared.submit(request)
        } else {
            BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: refreshTaskID)
        }
        return current?.boundary
    }
    /// The background wake-up (`AppDelegate` registers it before launch ends): the saved plan is applied for now.
    nonisolated static func handle(_ task: BGTask) {
        nonisolated(unsafe) let task = task
        let work = Task { _ = await apply() }
        task.expirationHandler = { work.cancel() }
        Task {
            await work.value
            task.setTaskCompleted(success: true)
        }
    }
    /// 사용 통계 `live_session_shown` once per Seoul day, when the activity is started.
    private static func reportShown(_ plan: Plan, now: Date) {
        let day = ActivityText.format(now, "yyyy-MM-dd", zone: ActivityText.seoul)
        guard UserDefaults.standard.string(forKey: shownDayKey) != day else { return }
        UserDefaults.standard.set(day, forKey: shownDayKey)
        AppAnalytics.event("live_session_shown", plan.analytics)
    }
    private nonisolated static func load() -> Plan? {
        UserDefaults.standard.data(forKey: planKey).flatMap { try? JSONDecoder().decode(Plan.self, from: $0) }
    }
    private nonisolated static func save(_ plan: Plan?) {
        if let data = plan.flatMap({ try? JSONEncoder().encode($0) }) {
            UserDefaults.standard.set(data, forKey: planKey)
        } else {
            UserDefaults.standard.removeObject(forKey: planKey)
        }
    }
}

