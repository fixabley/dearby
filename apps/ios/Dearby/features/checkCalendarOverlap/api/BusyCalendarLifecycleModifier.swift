import SwiftUI

/// Detail lifetime only; the app-scoped preferences owner handles scene and store changes once.
struct BusyCalendarLifecycleModifier: ViewModifier {
    let session: BusyCalendarSession
    let preferences: CalendarPreferences

    func body(content: Content) -> some View {
        content
            .onAppear { preferences.attach(session) }
            .onDisappear { preferences.detach(session) }
    }
}
