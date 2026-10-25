#if DEBUG
import Foundation

/// Store screenshots only (apps/ios/scripts/capture_screenshots.sh): `-DearbyScreenshot <scene>` on a Debug launch opens one screen.
/// Launch arguments land in `UserDefaults`; Release builds do not compile this.
enum ScreenshotScene: String {
    case discover, discoverExpanded = "discover-expanded", detail, mine, cards, settings, queue, favoritesCalendar = "favorites-calendar"
    static let current = UserDefaults.standard.string(forKey: "DearbyScreenshot").flatMap(Self.init)
}
#endif
