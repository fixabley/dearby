import SwiftUI

/// Opens an activity's detail by id from a screen outside 발견 (a history row whose role belongs to an activity, contract
/// "이력 (History) — 역할 매칭" › 표시). The app root puts one in the environment, bound to the 발견 tab's linked open; without it rows have no link.
struct ActivityOpener {
    let open: (String) -> Void
}
extension EnvironmentValues {
    @Entry var activityOpener: ActivityOpener?
}
