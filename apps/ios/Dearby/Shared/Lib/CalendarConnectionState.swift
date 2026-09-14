/// Values only; Shared UI never requests calendar permission or data.
enum CalendarConnectionState: Equatable, Sendable {
    case off, checking, consent, requesting, connected, denied, restricted, failed
}
