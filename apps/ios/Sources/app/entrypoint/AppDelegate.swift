import Observation
import OSLog
import UIKit
import UserNotifications

private let log = Logger(subsystem: "com.dearby.app", category: "push")

/// APNs callbacks for SwiftUI (contract "즐겨찾기(하트)·새 모집 푸시 알림" 2026-10-25): the device token and a tapped notification are kept here
/// until the app root reads them (a tap can arrive before the first scene). The payload is `aps.alert` plus `url` (the activity's web page,
/// or `dearby://favorites?view=calendar` for the day's summary), `activityId` and `notificationId` (contract "알림 목록").
@MainActor @Observable final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    private(set) var deviceToken: Data?
    /// The tapped notification's `url`, else its `activityId`; a local 세션 이동 알림 (contract "찜한 세션 이동 알림") opens its block's
    /// timetable on 내 시간표. The root opens it and clears it.
    enum Tap: Equatable { case link(URL), activity(String), timetable(activityID: String, scheduleID: String) }
    var tapped: Tap?
    /// The tapped notification's `notificationId` (알림 목록): it is marked read.
    var tappedNotification: Int64?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        self.deviceToken = deviceToken
    }
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // A missing `aps-environment` entitlement or provisioning profile lands here.
        log.warning("Remote notification registration failed: \(String(describing: error), privacy: .public)")
    }
    /// In the foreground the banner still shows.
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async
        -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        if info[SessionReminderModel.Key.kind] as? String == SessionReminderModel.kind,
           let activityID = info[SessionReminderModel.Key.activity] as? String, let scheduleID = info[SessionReminderModel.Key.schedule] as? String {
            // Local, so no `push_open` (analytics-v1.md lists the server's kinds).
            await MainActor.run { tapped = .timetable(activityID: activityID, scheduleID: scheduleID) }
            return
        }
        let url = (info["url"] as? String).flatMap(URL.init(string:))
        let activityID = (info["activityId"] as? NSNumber)?.stringValue ?? info["activityId"] as? String
        let notificationID = (info["notificationId"] as? NSNumber)?.int64Value ?? (info["notificationId"] as? String).flatMap { Int64($0) }
        await MainActor.run {
            // 사용 통계 `push_open`: the payload carries no kind, so only the day's summary (no `activityId`) is told apart.
            AppAnalytics.event("push_open", activityID == nil ? ["kind": "summary"] : [:])
            tappedNotification = notificationID
            tapped = url.map(Tap.link) ?? activityID.map(Tap.activity)
        }
    }
}
