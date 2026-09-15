import SwiftUI
import EventKit

struct BusyCalendarLifecycleModifier: ViewModifier {
    @Environment(\.scenePhase) private var scenePhase
    let session: BusyCalendarSession
    let preferences: CalendarPreferences

    func body(content: Content) -> some View {
        content
            .onAppear { preferences.attach(session) }
            .onDisappear { preferences.detach(session) }
            .onChange(of: scenePhase) { _, phase in
                switch phase {
                case .active: session.lifecycle(.active)
                case .background: session.lifecycle(.background)
                default: session.lifecycle(.inactive)
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .EKEventStoreChanged)) { _ in
                preferences.refreshAuthorization()
                session.refresh()
            }
    }
}
