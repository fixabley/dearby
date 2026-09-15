#if DEBUG
import Foundation

/// Explicit, isolated screenshot fixture. Never constructs EventKit or reads personal data.
actor PreviewBusyCalendarProvider: BusyCalendarProvider {
    let mode: String
    private var access: BusyCalendarAuthorization = .notRequested
    init(mode: String) { self.mode = mode }
    func authorization() -> BusyCalendarAuthorization { access }
    func requestReadPermission() async throws -> BusyCalendarAuthorization {
        try await Task.sleep(for: .milliseconds(300))
        access = mode == "denied" ? .denied : .fullAccess
        return access
    }
    func intervals(in range: DateInterval) async throws -> [BusyTimeInterval] {
        try await Task.sleep(for: .milliseconds(mode == "slow" ? 3000 : 400))
        if mode == "failure" { throw BusyCalendarFailure.unavailable }
        if mode == "empty" { return [] }
        // Fixed synthetic fixture, within the requested day only; no title/ID/location fields.
        let startHour: Double = mode == "touch" ? 16 : (mode == "separate" ? 12 : 15)
        let duration: Double = mode == "touch" ? 1 : 2
        return [BusyTimeInterval(start: range.start.addingTimeInterval(startHour * 3600), end: range.start.addingTimeInterval((startHour + duration) * 3600))!]
    }
    func discard() {}
}
#endif
