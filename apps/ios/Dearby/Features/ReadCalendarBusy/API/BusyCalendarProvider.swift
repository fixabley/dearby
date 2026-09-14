import Foundation

enum BusyCalendarAuthorization: Sendable { case notRequested, fullAccess, denied, restricted }
enum BusyCalendarFailure: Error { case unavailable, authorizationChanged }
protocol BusyCalendarProvider: Sendable {
    func authorization() async -> BusyCalendarAuthorization
    func requestReadPermission() async throws -> BusyCalendarAuthorization
    func intervals(in range: DateInterval) async throws -> [BusyTimeInterval]
    func discard() async
}
