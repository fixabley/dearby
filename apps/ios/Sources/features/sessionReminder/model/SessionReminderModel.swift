import Foundation
import Observation
import UserNotifications

/// 찜한 세션 이동 알림 (contract "찜한 세션 이동 알림" 2026-10-26): one local notification 10 minutes before each favorited session, built on
/// the device from `GET /v1/me/session-favorites` (no server part). Every change of the favorites and every return to the foreground
/// reschedules everything: the pending `session-*` requests are removed and the future ones are added again, nearest first, at most 60
/// (iOS keeps 64 pending local notifications). Nothing is scheduled without the notification permission or with the switch off.
@MainActor @Observable final class SessionReminderModel {
    /// `세션 이동 알림` (설정 › 알림): on until turned off, kept on the device.
    var enabled: Bool {
        didSet { UserDefaults.standard.set(enabled, forKey: Self.enabledKey) }
    }
    static let enabledKey = "dearby.sessionReminders.enabled"
    nonisolated static let identifierPrefix = "session-"
    nonisolated static let lead: TimeInterval = 10 * 60
    nonisolated static let limit = 60
    /// `userInfo` of a reminder: the app opens that activity's timetable sheet on 찜한 세션.
    enum Key { static let kind = "kind", activity = "activityId", schedule = "scheduleId", session = "sessionId" }
    nonisolated static let kind = "session_reminder"
    @ObservationIgnored private var running: Task<Void, Never>?
    /// The favorites of the last `reschedule`, for `refresh` after the permission was asked.
    @ObservationIgnored private var items: [FavoriteSession] = []
    init() {
        enabled = UserDefaults.standard.object(forKey: Self.enabledKey) as? Bool ?? true
    }

    /// Replaces every pending reminder with the ones `items` need from now on; a run still going is cancelled first.
    func reschedule(_ items: [FavoriteSession]) {
        self.items = items
        refresh()
    }
    /// The same favorites again: after the permission sheet, the switch or a return to the foreground.
    func refresh() {
        running?.cancel()
        running = Task { await replace(enabled ? Self.requests(items, now: Date()) : []) }
    }
    private func replace(_ requests: [UNNotificationRequest]) async {
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(Self.identifierPrefix) }
        guard !Task.isCancelled else { return }
        center.removePendingNotificationRequests(withIdentifiers: pending)
        let status = await center.notificationSettings().authorizationStatus
        guard !Task.isCancelled, [.authorized, .provisional, .ephemeral].contains(status), !requests.isEmpty else { return }
        for request in requests { try? await center.add(request) }
        AppAnalytics.event("session_reminder_scheduled", ["count": requests.count])
    }
    /// The reminders in start order: sessions at the same instant share one (`session-<smallest id>`, `같은 시간에 찜한 세션 n개`).
    nonisolated static func requests(_ items: [FavoriteSession], now: Date) -> [UNNotificationRequest] {
        let future = items.filter { $0.session.start.addingTimeInterval(-lead) > now }.sorted { ($0.session.start, $0.id) < ($1.session.start, $1.id) }
        var groups: [[FavoriteSession]] = []
        for item in future {
            if let last = groups.last, last[0].session.start == item.session.start { groups[groups.count - 1].append(item) } else { groups.append([item]) }
        }
        return groups.prefix(limit).map { group in
            let first = group[0]
            let content = UNMutableNotificationContent()
            content.title = (["10분 뒤", first.track?.name, first.track?.room] as [String?]).compactMap { $0 }.joined(separator: " · ")
            content.body = group.count > 1 ? first.session.title + "\n같은 시간에 찜한 세션 \(group.count)개" : first.session.title
            content.sound = .default
            content.userInfo = [Key.kind: kind, Key.activity: first.activityID, Key.schedule: first.scheduleID, Key.session: first.id]
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: first.session.start.addingTimeInterval(-lead).timeIntervalSince(now), repeats: false)
            return UNNotificationRequest(identifier: identifierPrefix + String(first.id), content: content, trigger: trigger)
        }
    }
}
