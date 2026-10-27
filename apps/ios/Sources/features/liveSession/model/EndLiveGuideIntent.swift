import ActivityKit
import AppIntents
import Foundation

/// `안내 종료` on the Live Activity (contract "행사 당일 진행 중 표시", 행사 안내 2026-10-27). Compiled into the app and `DearbyWidgets`; a
/// `LiveActivityIntent` runs in the app's process, so it turns the guide off in the app's `UserDefaults` and ends the activity at once.
struct EndLiveGuideIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "안내 종료"
    func perform() async throws -> some IntentResult {
        UserDefaults.standard.set("off", forKey: LiveSessionAttributes.guideKey)
        for activity in Activity<LiveSessionAttributes>.activities { await activity.end(nil, dismissalPolicy: .immediate) }
        return .result()
    }
}
