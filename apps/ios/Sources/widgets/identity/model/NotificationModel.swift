import Foundation
import Observation
import UIKit
import UserNotifications

/// 푸시 알림 (contract "즐겨찾기(하트)·새 모집 푸시 알림" 2026-10-25). The permission is never asked at launch: only the first program heart
/// (`requestPermissionIfNeeded`) or turning 새 모집 알림 on asks, and a refusal is not asked again (the profile row links to Settings).
/// Remote notifications are registered only once permission is granted; the APNs token goes to `PUT /v1/me/push-devices` with the
/// device's time zone while signed in and 새 모집 알림 is not off, and leaves the account on sign-out and when the switch goes off.
@MainActor @Observable final class NotificationModel {
    private(set) var permission = UNAuthorizationStatus.notDetermined
    /// The server's 새 모집 알림 (`notification_settings.newRecruitment`); nil until read or on a server without it (404).
    private(set) var newRecruitment: Bool?
    /// A one-shot message after a change could not be saved.
    var notice: String?
    /// This run's APNs token (hex); never logged.
    private var token: String?
    private let account: AccountViewModel
    init(account: AccountViewModel) {
        self.account = account
    }
    var denied: Bool { permission == .denied }
    private var granted: Bool { [.authorized, .provisional, .ephemeral].contains(permission) }

    /// Launch, return to the foreground, sign-in: reads the permission and the switch, and registers when permission was already given.
    func refresh() async {
        permission = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        if account.phase == .signedIn {
            let client = account.client
            do { newRecruitment = try await account.authorized { try await client.notificationSettings($0) }.newRecruitment } catch {
                if (error as? AccountError) == .notFound { newRecruitment = nil }
            }
        } else {
            newRecruitment = nil
        }
        if granted && account.phase == .signedIn { UIApplication.shared.registerForRemoteNotifications() }
    }
    /// The system permission sheet, only while it was never answered.
    func requestPermissionIfNeeded() async {
        permission = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        guard permission == .notDetermined else { return }
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
        await refresh()
    }
    /// APNs answered `registerForRemoteNotifications`.
    func registered(_ deviceToken: Data) {
        token = deviceToken.map { String(format: "%02x", $0) }.joined()
        Task { await upload() }
    }
    /// The device's time zone changed: the server sends at 8:00 in the new one.
    func timeZoneChanged() { Task { await upload() } }
    /// 새 모집 알림: saved at once and put back on failure. On also asks for permission when it was never answered; off removes this device.
    func setNewRecruitment(_ on: Bool) async {
        let before = newRecruitment
        newRecruitment = on
        let client = account.client
        do {
            try await account.authorized { try await client.saveNotificationSettings(.init(newRecruitment: on), $0) }
        } catch {
            newRecruitment = before
            notice = "알림 설정을 저장하지 못했어요. 잠시 후 다시 시도해 주세요."
            return
        }
        if on {
            await requestPermissionIfNeeded()
            await upload()
        } else if let token {
            _ = try? await account.authorized { try await client.deletePushDevice(token: token, $0) }
        }
    }
    /// Before 로그아웃 ends `session`: this device's token leaves the account.
    func signingOut(_ session: TokenPair) async {
        newRecruitment = nil
        guard let token else { return }
        try? await account.client.deletePushDevice(token: token, session)
    }
    private func upload() async {
        guard let token, account.phase == .signedIn, newRecruitment != false else { return }
        let client = account.client, zone = TimeZone.current.identifier
        // Best effort: the next launch sends it again.
        _ = try? await account.authorized { try await client.putPushDevice(token: token, timeZone: zone, $0) }
    }
}
