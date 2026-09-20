import Observation

/// Settings presentation state; permission and persisted preferences have one feature owner.
@MainActor @Observable
final class SettingsViewModel {
    let preferences: CalendarPreferences
    var isPresented = false

    init(preferences: CalendarPreferences) { self.preferences = preferences }
    func present() { isPresented = true }
    func dismiss() { isPresented = false }
}
