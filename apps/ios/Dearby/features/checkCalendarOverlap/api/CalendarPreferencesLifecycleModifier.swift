import SwiftUI
import EventKit

/// Installed once at the settings/root presentation, not once for each open detail.
struct CalendarPreferencesLifecycleModifier: ViewModifier {
    @Environment(\.scenePhase) private var scenePhase
    let preferences: CalendarPreferences

    func body(content: Content) -> some View {
        content
            .onReceive(NotificationCenter.default.publisher(for: .EKEventStoreChanged)) { _ in
                preferences.refreshAuthorization()
            }
            .onChange(of: scenePhase, initial: true) { _, phase in
                switch phase {
                case .active: preferences.lifecycle(.active)
                case .background: preferences.lifecycle(.background)
                default: preferences.lifecycle(.inactive)
                }
            }
    }
}
