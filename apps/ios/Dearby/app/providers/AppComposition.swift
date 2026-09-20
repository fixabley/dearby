/// App composition constructs the shared dependencies without implementing feature UI.
@MainActor
extension AppState {
    convenience init() {
        self.init(snapshotReader: BundleSnapshotReader(),
            favorites: FavoriteOrganizations(repository: UserDefaultsFavoriteOrganizationsRepository()),
            calendarPreferences: CalendarPreferences(store: BusyCalendarProviderFactory.makePreferenceStore(), provider: BusyCalendarProviderFactory.make()))
    }
}
