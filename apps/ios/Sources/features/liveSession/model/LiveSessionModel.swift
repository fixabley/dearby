import ActivityKit
import BackgroundTasks
import Foundation
import Observation
import OSLog

private let log = Logger(subsystem: "com.dearby.app", category: "liveSession")

/// 행사 당일 진행 중 표시 (contract "행사 당일 진행 중 표시", 행사 안내 2026-10-27): a Live Activity (잠금 화면·Dynamic Island) the user starts with
/// `행사 안내 시작` — like a navigation app's 안내 시작 — for the activity the 오늘 내 행사 card speaks for (`TodayEvent.pick`, today) when 내 시간표 has
/// sessions of it that day. It shows at once (before the first session in 이동) and ends 15 minutes after the last one, or at `안내 종료` (the app or
/// the activity's button, `EndLiveGuideIntent`). Nothing starts on its own. Built on the device from what the app already holds; nothing goes to the
/// server and the activity has no push token. The day's plan is kept on the device so a `BGAppRefreshTask` asked for each session boundary can move
/// the display on without the account data (best effort; the countdown runs in the widget, `Text(timerInterval:)`). iOS ends a Live Activity after
/// 8 hours and the user may swipe it away; the app does not bring it back — `guide` turns `.dismissed` and the button reads `안내 다시 시작`.
@MainActor @Observable final class LiveSessionModel {
    enum Guide: String { case off, on, dismissed }
    /// The guide's state, kept on the device (`LiveSessionAttributes.guideKey`); read again after each `apply`.
    private(set) var guide: Guide
    /// Today's plan, whether or not the guide runs: the 행사 안내 buttons show for its activity until its last session ends.
    private(set) var plan: Plan?
    nonisolated static let refreshTaskID = "io.wid.dearby.liveSession.refresh"
    /// How long the ended activity stays on the 잠금 화면 after the last session.
    nonisolated static let linger: TimeInterval = 15 * 60
    private nonisolated static let planKey = "dearby.liveSession.plan"
    private nonisolated static let shownDayKey = "dearby.liveSession.shownDay"
    @ObservationIgnored private var applying: Task<Void, Never>?
    @ObservationIgnored private var timer: Task<Void, Never>?
    #if DEBUG
    /// 라이브 액티비티 미리보기 (설정 › Debug): the mode shown in place of the saved plan (`togglePreview`); nil when off.
    var previewing: PreviewMode?
    @ObservationIgnored private var planBeforePreview: Plan?
    #endif

    init() {
        guide = Self.loadGuide()
        plan = Self.load()
    }

    /// The day's favorited sessions of the activity, in start order, with the 사용 통계 activity parameters of `live_session_shown`.
    struct Plan: Codable, Equatable {
        let attributes: LiveSessionAttributes
        let entries: [LiveSessionAttributes.ContentState.Entry]
        let analytics: [String: String]
        /// Some session is not over yet.
        func open(at now: Date) -> Bool { entries.contains { $0.end > now } }
    }
    /// What the activity shows at one moment: nothing (`state` nil) once the last session is over, and when the next change is due.
    struct Moment: Equatable {
        let state: LiveSessionAttributes.ContentState?
        let boundary: Date?
    }

    /// The account data arrived or changed (`Plan.today`, widgets layer): the plan is saved and applied; nil (signed out, nothing today) ends the guide.
    func sync(_ plan: Plan?, now: Date = Date()) {
        Self.save(plan)
        self.plan = plan
        if plan == nil { Self.saveGuide(.off) }
        refresh(now: now)
    }
    /// `행사 안내 시작` (and `안내 다시 시작`): the activity shows at once.
    func start(now: Date = Date()) {
        guard let plan, plan.open(at: now) else { return }
        Self.saveGuide(.on)
        guide = .on
        refresh(now: now, starting: true)
    }
    /// `안내 종료` in the app: the activity ends at once.
    func stop(now: Date = Date()) {
        Self.saveGuide(.off)
        guide = .off
        refresh(now: now)
    }
    /// The saved plan again: at launch, on return to the foreground, and at each session boundary while the app is open.
    func refresh(now: Date = Date(), starting: Bool = false) {
        timer?.cancel()
        // One `apply` at a time: a run still going finishes first (ActivityKit calls are not cancellable), so no second request slips in.
        let previous = applying
        previous?.cancel()
        applying = Task { [weak self] in
            await previous?.value
            let boundary = await Self.apply(now: now, starting: starting)
            self?.guide = Self.loadGuide()
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

    /// From now to the end of the last session: `current` is the first session not over (before it starts, the one to walk to), `next` the
    /// first starting after it and not over. The boundary is the nearest start or end still ahead.
    nonisolated static func moment(_ plan: Plan, now: Date) -> Moment {
        let entries = plan.entries
        guard let current = entries.first(where: { $0.end > now }) else { return Moment(state: nil, boundary: nil) }
        let next = entries.first { $0.start > current.start && $0.end > now }
        let boundary = entries.flatMap { [$0.start, $0.end] }.filter { $0 > now }.min()
        let position = entries.firstIndex(of: current) ?? 0
        return Moment(state: .init(current: current, next: next, running: current.start <= now, position: position, total: entries.count),
                      boundary: boundary)
    }
    /// The gate `apply` goes through: nothing unless the guide runs and a plan is saved (a sign-out clears both).
    nonisolated static func moment(guiding: Bool, plan: Plan?, now: Date) -> Moment? {
        guard guiding, let plan else { return nil }
        return moment(plan, now: now)
    }

    /// Updates or ends the activity from the saved plan and the guide; `starting` (the button) may request a new one. A running guide whose
    /// activity is gone (swiped away, or ended by iOS) turns `.dismissed` instead of coming back; after the last session it ends, staying 15
    /// minutes, and the guide turns off. Asks for a background wake-up at the next boundary and returns it. Runs for the app (`refresh`) and the
    /// background task alike; nonisolated so the activities never cross an actor.
    @discardableResult nonisolated static func apply(now: Date = Date(), starting: Bool = false) async -> Date? {
        let plan = load()
        let current = moment(guiding: loadGuide() == .on, plan: plan, now: now)
        let activities = Activity<LiveSessionAttributes>.activities.filter { [.active, .stale].contains($0.activityState) }
        var boundary: Date?
        if let plan, let state = current?.state {
            // Stale at the next boundary: if the app is not woken there, the system dims what would be out of date.
            let content = ActivityContent(state: state, staleDate: current?.boundary)
            let kept = activities.first { $0.attributes == plan.attributes }
            for activity in activities where activity.id != kept?.id { await activity.end(nil, dismissalPolicy: .immediate) }
            if let kept {
                await kept.update(content)
                boundary = current?.boundary
            } else if starting, ActivityAuthorizationInfo().areActivitiesEnabled {
                do {
                    _ = try Activity.request(attributes: plan.attributes, content: content)
                    await reportShown(plan, now: now)
                    boundary = current?.boundary
                } catch {
                    log.warning("Live Activity request failed: \(String(describing: error), privacy: .public)")
                    saveGuide(.off)
                }
            } else {
                saveGuide(starting ? .off : .dismissed)
            }
        } else if let plan, current != nil, let last = plan.entries.map(\.end).max() {
            // The day is over: the last look stays a little, then goes.
            for activity in activities { await activity.end(nil, dismissalPolicy: .after(last.addingTimeInterval(linger))) }
            saveGuide(.off)
        } else {
            for activity in activities { await activity.end(nil, dismissalPolicy: .immediate) }
        }
        if let boundary {
            let request = BGAppRefreshTaskRequest(identifier: refreshTaskID)
            request.earliestBeginDate = boundary
            // Unavailable on the simulator and when Background App Refresh is off; the foreground timer still runs.
            try? BGTaskScheduler.shared.submit(request)
        } else {
            BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: refreshTaskID)
        }
        return boundary
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
        #if DEBUG
        if plan.attributes.activityID == previewActivityID { return }
        #endif
        let day = ActivityText.format(now, "yyyy-MM-dd", zone: ActivityText.seoul)
        guard UserDefaults.standard.string(forKey: shownDayKey) != day else { return }
        UserDefaults.standard.set(day, forKey: shownDayKey)
        AppAnalytics.event("live_session_shown", plan.analytics)
    }
    private nonisolated static func loadGuide() -> Guide {
        UserDefaults.standard.string(forKey: LiveSessionAttributes.guideKey).flatMap(Guide.init) ?? .off
    }
    private nonisolated static func saveGuide(_ guide: Guide) {
        UserDefaults.standard.set(guide.rawValue, forKey: LiveSessionAttributes.guideKey)
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

#if DEBUG
extension LiveSessionModel {
    nonisolated static let previewActivityID = "preview"
    /// What the preview shows: a session running (세션 중), or the gap before the next one (이동).
    enum PreviewMode: CaseIterable { case running, moving }
    /// 라이브 액티비티 미리보기 (설정 › Debug, 2026-10-27 사용자 결정): a made-up 행사 `Dearby 미리보기` of four sessions, started at once through the
    /// real path (`sync` → `start`) with no account. Each tap moves on — 세션 중, then 이동 — and the next one ends it and puts the saved plan back;
    /// `DearbyApp.syncLiveSession` leaves the plan alone meanwhile. Not compiled into Release; not counted in 사용 통계.
    func togglePreview(now: Date = Date()) {
        let modes = PreviewMode.allCases
        let next = previewing.flatMap { modes.firstIndex(of: $0) }.map { $0 + 1 } ?? 0
        if previewing == nil { planBeforePreview = Self.load() }
        if next < modes.count {
            previewing = modes[next]
            Self.save(Self.previewPlan(modes[next], now: now))
            plan = Self.load()
            start(now: now)
        } else {
            previewing = nil
            stop(now: now)
            Self.save(planBeforePreview)
            plan = planBeforePreview
            planBeforePreview = nil
        }
    }
    /// 세션 중: the second session runs for 7 more minutes. 이동: the second ended 3 minutes ago and the third starts in 7, in another room.
    nonisolated static func previewPlan(_ mode: PreviewMode, now: Date) -> Plan {
        func entry(_ title: String, _ track: Int, _ room: String, _ from: Double, _ to: Double) -> LiveSessionAttributes.ContentState.Entry {
            .init(scheduleID: previewActivityID, title: title, track: "트랙 " + ["A", "B", "C"][track], room: room,
                  start: now.addingTimeInterval(from * 60), end: now.addingTimeInterval(to * 60), trackPosition: track)
        }
        let entries = switch mode {
        case .running: [entry("키노트: 행사의 시작", 0, "그랜드볼룸 A", -90, -50), entry("모바일 앱 디자인 시스템 만들기", 0, "그랜드볼룸 A", -20, 7),
                        entry("라이브 액티비티 실전", 1, "그랜드볼룸 B", 12, 40), entry("마무리 토크", 2, "세미나실 3", 50, 80)]
        case .moving: [entry("키노트: 행사의 시작", 0, "그랜드볼룸 A", -90, -50), entry("모바일 앱 디자인 시스템 만들기", 0, "그랜드볼룸 A", -35, -3),
                       entry("라이브 액티비티 실전", 1, "그랜드볼룸 B", 7, 35), entry("마무리 토크", 2, "세미나실 3", 50, 80)]
        }
        return Plan(attributes: LiveSessionAttributes(activityID: previewActivityID, activityTitle: "Dearby 미리보기", zone: "Asia/Seoul"),
                    entries: entries, analytics: [:])
    }
}
#endif
