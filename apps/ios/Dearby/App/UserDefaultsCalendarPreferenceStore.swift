import Foundation

/// Stores only non-personal feature booleans, never calendar intervals or metadata.
@MainActor final class UserDefaultsCalendarPreferenceStore: CalendarPreferenceStore {
    private let defaults: UserDefaults
    init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    var enabled: Bool {
        get { defaults.bool(forKey: "dearby.calendarBusy.enabled") }
        set { defaults.set(newValue, forKey: "dearby.calendarBusy.enabled") }
    }
    var firstPromptHandled: Bool {
        get { defaults.bool(forKey: "dearby.calendarBusy.firstPromptHandled") }
        set { defaults.set(newValue, forKey: "dearby.calendarBusy.firstPromptHandled") }
    }
}
