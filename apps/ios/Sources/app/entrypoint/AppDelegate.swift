import Observation
import OSLog
import UIKit
import UserNotifications

private let log = Logger(subsystem: "com.dearby.app", category: "push")

/// APNs callbacks for SwiftUI (contract "즐겨찾기(하트)·새 모집 푸시 알림" 2026-10-25): the device token and a tapped notification are kept here
/// until the app root reads them (a tap can arrive before the first scene). The payload is `aps.alert` plus `url` (the activity's web page,
/// or `dearby://favorites?view=calendar` for the day's summary) and `activityId`.
@MainActor @Observable final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    private(set) var deviceToken: Data?
    /// The tapped notification's `url`, else its `activityId`; the root opens it and clears it.
    enum Tap: Equatable { case link(URL), activity(String) }
    var tapped: Tap?

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
        let url = (info["url"] as? String).flatMap(URL.init(string:))
        let activityID = (info["activityId"] as? NSNumber)?.stringValue ?? info["activityId"] as? String
        await MainActor.run { tapped = url.map(Tap.link) ?? activityID.map(Tap.activity) }
    }
}
