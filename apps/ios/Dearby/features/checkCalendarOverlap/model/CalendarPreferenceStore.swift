import Foundation

@MainActor protocol CalendarPreferenceStore: AnyObject {
    var enabled: Bool { get set }
    var firstPromptHandled: Bool { get set }
}

@MainActor final class MemoryCalendarPreferenceStore: CalendarPreferenceStore {
    var enabled = false
    var firstPromptHandled = false
}
