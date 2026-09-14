import Foundation

actor FakeBusyProvider: BusyCalendarProvider {
    var access: BusyCalendarAuthorization = .notRequested
    var decision: BusyCalendarAuthorization = .fullAccess
    var values: [BusyTimeInterval] = []
    var fails = false
    var hold = false
    var pending: [CheckedContinuation<[BusyTimeInterval], Error>] = []
    var holdPermission = false
    var pendingPermission: CheckedContinuation<BusyCalendarAuthorization, Error>?
    var requested = 0
    var queries: [DateInterval] = []
    var discards = 0
    func authorization() -> BusyCalendarAuthorization { access }
    func requestReadPermission() async throws -> BusyCalendarAuthorization {
        requested += 1
        if holdPermission { return try await withCheckedThrowingContinuation { pendingPermission = $0 } }
        access = decision; return access
    }
    func holdPermissionResponse() { holdPermission = true }
    func permissionPending() -> Bool { pendingPermission != nil }
    func grantPermission() { access = .fullAccess; pendingPermission?.resume(returning: .fullAccess); pendingPermission = nil }
    func intervals(in range: DateInterval) async throws -> [BusyTimeInterval] {
        queries.append(range)
        if hold { return try await withCheckedThrowingContinuation { pending.append($0) } }
        if fails { throw BusyCalendarFailure.unavailable }
        return values
    }
    func discard() { discards += 1 }
    func configure(access: BusyCalendarAuthorization? = nil, decision: BusyCalendarAuthorization? = nil,
                   values: [BusyTimeInterval]? = nil, fails: Bool? = nil, hold: Bool? = nil) {
        if let access { self.access = access }; if let decision { self.decision = decision }
        if let values { self.values = values }; if let fails { self.fails = fails }; if let hold { self.hold = hold }
    }
    func release(_ values: [BusyTimeInterval]) { let old = pending; pending.removeAll(); old.forEach { $0.resume(returning: values) } }
    func counts() -> (Int, Int, Int) { (requested, queries.count, pending.count) }
}

@main struct BusyCalendarTests {
    @MainActor static func main() async {
        func date(_ seconds: Double) -> Date { Date(timeIntervalSince1970: seconds) }
        func busy(_ a: Double, _ b: Double) -> BusyTimeInterval { .init(start: date(a), end: date(b))! }
        let day = DateInterval(start: date(0), end: date(86400))
        let activity = DateInterval(start: date(14 * 3600), end: date(16 * 3600))
        let overlaps = [busy(13 * 3600, 15 * 3600), busy(14 * 3600, 15.5 * 3600), busy(15.5 * 3600, 16 * 3600)]
        precondition(BusyTimeInterval.merged(overlaps, in: day) == [busy(13 * 3600, 16 * 3600)])
        precondition(BusyTimeInterval.merged([busy(-86400, 0), busy(86400, 90000)], in: day).isEmpty)
        precondition(BusyTimeInterval.merged([busy(-86400, 86400)], in: activity) == [busy(14 * 3600, 16 * 3600)])
        for flags in [(true,false,false),(false,true,false),(false,false,true)] {
            precondition(BusyCalendarOccurrence(start: date(10), end: date(20), cancelled: flags.0,
                explicitlyFree: flags.1, declinedByMe: flags.2).interval == nil)
        }
        // Occurrences are already expanded by the OS; same identifiers are not used to collapse repetitions.
        let occurrences = [busy(100, 200), busy(300, 400), busy(100, 200)]
        precondition(BusyTimeInterval.merged(occurrences, in: day) == [busy(100, 200), busy(300, 400)])
        precondition(BusyTimeInterval(start: date(20), end: date(10)) == nil)
        // Warning uses positive intersection, never a visual minimum height or a touching edge.
        precondition(BusyTimeInterval.merged([busy(16*3600,17*3600)], in: activity).isEmpty)
        precondition(BusyTimeInterval.merged([busy(12*3600,14*3600)], in: activity).isEmpty)
        precondition(BusyTimeInterval.merged([busy(16*3600-1,17*3600)], in: activity) == [busy(16*3600-1,16*3600)])
        let utc = TimeZone(secondsFromGMT: 0)!
        precondition(busy(15*3600,17*3600).description(in: utc) == "오후 3시부터 오후 5시까지")
        precondition(busy(15*3600+30,15*3600+31).description(in: utc).contains("30초"))
        precondition(busy(23*3600,86400).description(in: utc).contains("1월 2일"))
        let iso = ISO8601DateFormatter(), zone = TimeZone(identifier: "America/New_York")!
        let dst = BusyTimeInterval(start: iso.date(from: "2026-11-01T05:30:00Z")!, end: iso.date(from: "2026-11-01T06:30:00Z")!)!
        precondition(dst.description(in: zone).contains("-04:00") && dst.description(in: zone).contains("-05:00"))
        print("PASS positive overlap/touch/one-second boundaries; Korean times, midnight and DST repeated-hour offsets")
        @MainActor func wait(_ condition: @MainActor () async -> Bool) async {
            for _ in 0..<500 { if await condition() { return }; try? await Task.sleep(for: .milliseconds(2)) }
            preconditionFailure("fake condition timed out")
        }
        let provider = FakeBusyProvider(), session = BusyCalendarSession(provider: FakeBusyProvider())
        session.close() // Closing an unused session must not ask for permission.
        let owner = BusyCalendarSession(provider: provider)
        owner.select(id: 0, day: day, activity: activity)
        precondition(!owner.isEnabled && owner.connection == .off && owner.days.isEmpty)
        let initial = await provider.counts(); precondition(initial.0 == 0 && initial.1 == 0)
        owner.setEnabled(true)
        await wait { owner.connection == .consent }
        precondition(!owner.isEnabled)
        owner.cancelConsent(); precondition(owner.connection == .off && owner.days.isEmpty)
        let cancelled = await provider.counts(); precondition(cancelled.0 == 0 && cancelled.1 == 0)
        owner.setEnabled(true); await wait { owner.connection == .consent }
        await provider.configure(values: overlaps)
        owner.continueConsent(); await wait { owner.days[0]?.status == .ready }
        precondition(owner.isEnabled && owner.days[0]!.overlaps == [busy(14*3600,16*3600)])
        let connected = await provider.counts(); precondition(connected.0 == 1)
        owner.select(id: 1, day: day, activity: DateInterval(start: date(20*3600), end: date(21*3600)))
        await wait { owner.days[1]?.status == .ready }
        precondition(owner.days[1]!.overlaps.isEmpty)
        let shared = await provider.counts(); precondition(shared.0 == 1)
        owner.setEnabled(false); precondition(owner.days.isEmpty && !owner.isEnabled)
        owner.setEnabled(true); await wait { owner.days[0]?.status == .ready }
        let reused = await provider.counts(); precondition(reused.0 == 1)
        await provider.configure(values: [], fails: true)
        owner.refresh(); await wait { owner.days[0]?.status == .failed }
        precondition(owner.days[0]!.intervals.isEmpty && owner.days[0]!.status != .ready)
        await provider.configure(fails: false)
        owner.refresh(); await wait { owner.days[0]?.status == .ready }
        precondition(owner.days[0]!.intervals.isEmpty)
        await provider.configure(hold: true)
        owner.refresh(); await wait { await provider.counts().2 == 1 }
        precondition(owner.days[0]?.status == .loading)
        owner.setEnabled(false); precondition(owner.days.isEmpty)
        await provider.release(overlaps)
        try? await Task.sleep(for: .milliseconds(20))
        precondition(owner.days.isEmpty && !owner.isEnabled)
        await provider.configure(hold: false)
        owner.setEnabled(true); await wait { owner.days[0]?.status == .ready }
        await provider.configure(access: .denied)
        owner.refresh(); await wait { owner.connection == .denied }
        precondition(owner.days.isEmpty && !owner.isEnabled)
        await provider.configure(access: .restricted)
        owner.setEnabled(true); await wait { owner.connection == .restricted }
        await provider.configure(access: .notRequested, decision: .denied)
        owner.setEnabled(true); await wait { owner.connection == .consent }
        owner.continueConsent(); await wait { owner.connection == .denied }
        precondition(!owner.isEnabled && owner.days.isEmpty)
        await provider.configure(access: .fullAccess, hold: true)
        owner.setEnabled(true); await wait { await provider.counts().2 == 1 }
        let nextDay = DateInterval(start: date(86400), end: date(172800))
        owner.select(id: 0, day: nextDay, activity: nextDay)
        await wait { await provider.counts().2 == 2 }
        await provider.configure(hold: false)
        await provider.release(overlaps)
        await wait { owner.days[0]?.status == .ready }
        precondition(owner.days[0]!.intervals.isEmpty) // late old day never replaces the new selection
        owner.suspend(); precondition(owner.days.isEmpty)
        owner.resume(); await wait { owner.days[0]?.status == .ready }
        await provider.configure(hold: true)
        owner.refresh(); await wait { await provider.counts().2 == 1 }
        owner.close(); await provider.release(overlaps)
        try? await Task.sleep(for: .milliseconds(20))
        precondition(!owner.isEnabled && owner.days.isEmpty)
        let promptProvider = FakeBusyProvider(), promptOwner = BusyCalendarSession(provider: FakeBusyProvider())
        promptOwner.close()
        let prompt = BusyCalendarSession(provider: promptProvider)
        await promptProvider.holdPermissionResponse()
        prompt.setEnabled(true); await wait { prompt.connection == .consent }
        prompt.continueConsent(); prompt.cancelConsent() // alert dismissal after Continue is not a cancel
        await wait { await promptProvider.permissionPending() }
        prompt.lifecycle(.inactive)
        precondition(prompt.connection == .requesting && prompt.isEnabled)
        await promptProvider.grantPermission(); await wait { prompt.connection == .connected }
        prompt.lifecycle(.active); precondition(prompt.isEnabled)
        prompt.close()
        await promptProvider.configure(access: .notRequested)
        prompt.setEnabled(true); await wait { prompt.connection == .consent }
        prompt.continueConsent(); await wait { await promptProvider.permissionPending() }
        prompt.lifecycle(.background); await promptProvider.grantPermission()
        try? await Task.sleep(for: .milliseconds(20))
        precondition(!prompt.isEnabled && prompt.connection == .off && prompt.days.isEmpty)
        prompt.lifecycle(.active); precondition(!prompt.isEnabled)
        print("PASS: permission prompt inactive/Continue-dismissal accepted; background late grant ignored")
        print("PASS: half-open overlap/merge/adjacency/all-day/expanded occurrences/filter; OFF/consent cancel/continue/shared permission/reuse/denied/restricted/error/empty; loading OFF and stale day suppression; revoke/background/resume/close")
    }
}
