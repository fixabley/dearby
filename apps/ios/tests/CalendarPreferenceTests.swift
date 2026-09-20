import Foundation

@MainActor func testCalendarPreferences() async {
    func wait(_ condition: @MainActor () async -> Bool) async {
        for _ in 0..<500 { if await condition() { return }; try? await Task.sleep(for: .milliseconds(2)) }
        preconditionFailure("preference condition timed out")
    }
    let store = MemoryCalendarPreferenceStore(), provider = FakeBusyProvider()
    var prefs = CalendarPreferences(store: store, provider: provider)
    prefs.start(); precondition(prefs.showFirstPrompt && !prefs.enabled)
    prefs.later(); prefs.start()
    precondition(!prefs.showFirstPrompt && store.firstPromptHandled && !store.enabled)
    let deferredCounts = await provider.counts(); precondition(deferredCounts.0 == 0 && deferredCounts.1 == 0)
    prefs = CalendarPreferences(store: store, provider: provider)
    prefs.start(); precondition(!prefs.showFirstPrompt)
    await provider.holdPermissionResponse()
    prefs.setEnabled(true); await wait { await provider.permissionPending() }
    prefs.lifecycle(.inactive)
    await provider.grantPermission(); await wait { prefs.enabled }
    precondition(store.enabled)
    let firstCount = await provider.counts(); precondition(firstCount.0 == 1)
    let restarted = CalendarPreferences(store: store, provider: provider)
    restarted.start(); await wait { restarted.connection == .connected }
    precondition(restarted.enabled && !restarted.showFirstPrompt)
    restarted.setEnabled(false); restarted.setEnabled(true); await wait { restarted.enabled }
    let reused = await provider.counts(); precondition(reused.0 == 1)

    let start = Date(timeIntervalSince1970: 0), end = start.addingTimeInterval(86400)
    let detail = BusyCalendarSession(provider: provider)
    detail.select(id: 0, day: DateInterval(start: start, end: end), activity: DateInterval(start: start, end: end))
    await provider.configure(hold: true)
    restarted.attach(detail); await wait { await provider.counts().2 == 1 }
    restarted.setEnabled(false)
    precondition(detail.days.isEmpty && !detail.isEnabled && !store.enabled)
    await provider.release([BusyTimeInterval(start: start, end: end)!])
    try? await Task.sleep(for: .milliseconds(20))
    precondition(detail.days.isEmpty && !detail.isEnabled)
    await provider.configure(hold: false)
    restarted.setEnabled(true); await wait { detail.days[0]?.status == .ready }
    restarted.lifecycle(.background); precondition(detail.days.isEmpty)
    let backgroundQueries = await provider.counts().1
    restarted.refreshAuthorization() // Same entrypoint as EKEventStoreChanged while backgrounded.
    try? await Task.sleep(for: .milliseconds(30))
    precondition(detail.days.isEmpty, "Background calendar notification must not resume personal results")
    let afterBackgroundQueries = await provider.counts().1
    precondition(afterBackgroundQueries == backgroundQueries)
    await provider.configure(access: .denied)
    restarted.lifecycle(.active); await wait { restarted.connection == .denied }
    precondition(!restarted.enabled && !store.enabled && detail.days.isEmpty)
    restarted.detach(detail)

    let deniedStore = MemoryCalendarPreferenceStore(), deniedProvider = FakeBusyProvider()
    await deniedProvider.configure(decision: .denied)
    let denied = CalendarPreferences(store: deniedStore, provider: deniedProvider)
    denied.start(); denied.enableFromFirstPrompt(); await wait { denied.connection == .denied }
    precondition(deniedStore.firstPromptHandled && !deniedStore.enabled)
    let lateProvider = FakeBusyProvider(), late = CalendarPreferences(store: MemoryCalendarPreferenceStore(), provider: FakeBusyProvider())
    late.later()
    await lateProvider.holdPermissionResponse()
    let pending = CalendarPreferences(store: MemoryCalendarPreferenceStore(), provider: lateProvider)
    pending.setEnabled(true); await wait { await lateProvider.permissionPending() }
    pending.setEnabled(false); await lateProvider.grantPermission()
    try? await Task.sleep(for: .milliseconds(20))
    precondition(!pending.enabled && pending.connection == .off)

    let failedProvider = FakeBusyProvider()
    await failedProvider.failPermission()
    let failed = CalendarPreferences(store: MemoryCalendarPreferenceStore(), provider: failedProvider)
    failed.setEnabled(true); await wait { failed.connection == .failed }
    precondition(!failed.enabled)
    await failedProvider.configure(access: .restricted)
    failed.setEnabled(true); await wait { failed.connection == .restricted }
    precondition(!failed.enabled)

    let suite = "dearby.calendarBusy.tests.\(UUID().uuidString)"
    let defaults = UserDefaults(suiteName: suite)!
    defer { defaults.removePersistentDomain(forName: suite) }
    let disk = UserDefaultsCalendarPreferenceStore(defaults: defaults)
    disk.enabled = true; disk.firstPromptHandled = true
    let reloaded = UserDefaultsCalendarPreferenceStore(defaults: defaults)
    precondition(reloaded.enabled && reloaded.firstPromptHandled)
    let keys = defaults.persistentDomain(forName: suite)!.keys.sorted()
    precondition(keys == ["dearby.calendarBusy.enabled", "dearby.calendarBusy.firstPromptHandled"])
    print("PASS preferences: first prompt once/later zero permission/relaunch persistence/grant/deny/reuse/inactive; OFF cancels active detail and stale permission/query; foreground revocation; only two boolean keys persisted")
}

@MainActor func testCalendarLifecycle() async {
    func settle() async { try? await Task.sleep(for: .milliseconds(30)) }
    func wait(_ condition: @MainActor () async -> Bool) async {
        for _ in 0..<500 { if await condition() { return }; try? await Task.sleep(for: .milliseconds(2)) }
        preconditionFailure("calendar lifecycle condition timed out")
    }
    let provider = FakeBusyProvider()
    let prefs = CalendarPreferences(store: MemoryCalendarPreferenceStore(), provider: provider)
    let detail = BusyCalendarSession(provider: provider)
    let day = DateInterval(start: Date(timeIntervalSince1970: 0), duration: 86400)
    detail.select(id: 0, day: day, activity: day)
    prefs.attach(detail)
    prefs.start(); prefs.later()
    let beforeOptIn = await provider.counts()
    precondition(beforeOptIn.0 == 0 && beforeOptIn.1 == 0)
    await provider.holdPermissionResponse()
    prefs.enableFromFirstPrompt()
    await wait { await provider.permissionPending() }
    prefs.lifecycle(.inactive)
    await provider.grantPermission()
    await wait { detail.days[0]?.status == .ready }
    let initial = await provider.counts()
    precondition(initial.0 == 1 && initial.1 == 1)

    // The sole production EventKit notification entrypoint performs one detail query.
    prefs.refreshAuthorization()
    await wait { await provider.counts().1 == initial.1 + 1 }
    await settle()
    let refreshed = await provider.counts()
    precondition(refreshed.1 == initial.1 + 1)
    let noCancellation = await provider.cancelledQueries
    precondition(noCancellation == 0)

    // Authorization already in flight cannot publish after background.
    await provider.pauseAuthorization()
    prefs.refreshAuthorization()
    await wait { await provider.authorizationPending() }
    prefs.lifecycle(.background)
    await provider.releaseAuthorization()
    prefs.refreshAuthorization()
    await settle()
    let afterBackground = await provider.counts()
    precondition(detail.days.isEmpty && afterBackground.1 == refreshed.1)
    prefs.lifecycle(.active)
    await wait { detail.days[0]?.status == .ready }
    await settle()
    let resumed = await provider.counts()
    precondition(resumed.1 == refreshed.1 + 1)

    await provider.configure(hold: true)
    prefs.refreshAuthorization()
    await wait { await provider.counts().2 == 1 }
    prefs.lifecycle(.background)
    await provider.release([BusyTimeInterval(start: day.start, end: day.end)!])
    prefs.refreshAuthorization()
    await settle()
    let cancelled = await provider.cancelledQueries
    precondition(detail.days.isEmpty && cancelled == 1)
    await provider.configure(hold: false)
    prefs.lifecycle(.active)
    await wait { detail.days[0]?.status == .ready }
    prefs.detach(detail)
    precondition(detail.days.isEmpty && !detail.isEnabled)

    // A permission dialog is not cancelled by inactive, but its late grant after background is ignored.
    let lateProvider = FakeBusyProvider()
    let late = CalendarPreferences(store: MemoryCalendarPreferenceStore(), provider: lateProvider)
    await lateProvider.holdPermissionResponse()
    late.setEnabled(true); await wait { await lateProvider.permissionPending() }
    late.lifecycle(.background)
    await lateProvider.grantPermission(); await settle()
    precondition(!late.enabled && late.connection == .off)
    late.lifecycle(.active); await settle()
    precondition(!late.enabled)
    let backgroundStore = MemoryCalendarPreferenceStore()
    backgroundStore.enabled = true; backgroundStore.firstPromptHandled = true
    let initialProvider = FakeBusyProvider()
    let initiallyBackground = CalendarPreferences(store: backgroundStore, provider: initialProvider)
    initiallyBackground.lifecycle(.background)
    initiallyBackground.start(); initiallyBackground.refreshAuthorization()
    let initialDetail = BusyCalendarSession(provider: initialProvider)
    initiallyBackground.attach(initialDetail)
    await settle()
    let initialCalls = await initialProvider.authorizationCalls
    precondition(initialCalls == 0 && !initialDetail.isEnabled)
    print("PASS production preference lifecycle: opt-in inactive dialog, one query per notification/resume, background notification blocked, late authorization/query/grant ignored, cancelled query counted, detach clears")
}
