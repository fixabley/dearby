import SwiftUI

@main
struct DearbyApp: App {
    private let snapshotReader = BundleSnapshotReader()
    @State private var favorites = FavoriteOrganizations(repository: UserDefaultsFavoriteOrganizationsRepository())

    @State private var calendarPreferences = CalendarPreferences(store: BusyCalendarProviderFactory.makePreferenceStore(), provider: BusyCalendarProviderFactory.make())

    var body: some Scene {
        WindowGroup {
            ContentView(snapshotReader: snapshotReader, favorites: favorites, calendarPreferences: calendarPreferences)
        }
    }
}
