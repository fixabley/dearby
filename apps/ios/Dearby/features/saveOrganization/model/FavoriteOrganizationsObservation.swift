/// Releasing this token stops notifications; the action owner holds subscribers weakly.
@MainActor
final class FavoriteOrganizationsObservation {
    let onChange: @MainActor () -> Void
    init(onChange: @escaping @MainActor () -> Void) { self.onChange = onChange }
}
