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
}
