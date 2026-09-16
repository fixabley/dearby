/// App composition constructs dependencies and page destinations without implementing feature UI.
@MainActor
extension AppState {
    convenience init() {
        self.init(snapshotReader: BundleSnapshotReader(),
            favorites: FavoriteOrganizations(repository: UserDefaultsFavoriteOrganizationsRepository()),
            calendarPreferences: CalendarPreferences(store: BusyCalendarProviderFactory.makePreferenceStore(), provider: BusyCalendarProviderFactory.make()))
    }

    func detailPage(viewModel: NoticeDetailViewModel) -> NoticeDetailPage {
        NoticeDetailPage(viewModel: viewModel, preferences: calendarPreferences)
    }
}
