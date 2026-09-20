/// Feature permission/consent state; generic Shared timeline values do not model authorization.
enum CalendarConnectionState: Equatable, Sendable {
    case off, checking, requesting, connected, denied, restricted, failed
}
