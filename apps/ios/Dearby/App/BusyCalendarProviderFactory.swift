import Foundation

enum BusyCalendarProviderFactory {
    static func make() -> any BusyCalendarProvider {
        #if DEBUG
        if let mode = ProcessInfo.processInfo.arguments.first(where: { $0.hasPrefix("--busy-calendar-fixture=") }) {
            return PreviewBusyCalendarProvider(mode: String(mode.dropFirst("--busy-calendar-fixture=".count)))
        }
        #endif
        return EventKitBusyProvider()
    }
    @MainActor static func makePreferenceStore() -> any CalendarPreferenceStore {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains(where: { $0.hasPrefix("--busy-calendar-fixture=") }) {
            return MemoryCalendarPreferenceStore()
        }
        #endif
        return UserDefaultsCalendarPreferenceStore()
    }
}
