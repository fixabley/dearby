import XCTest
import SwiftData
import EventKit
@testable import Dearby

@MainActor final class CalendarConflictTests: XCTestCase {
    // Explicit user preview only. A one-shot marker is required; ordinary CI skips this.
    func testSeedPreviewCalendarWhenRequested() async throws {
        #if targetEnvironment(simulator)
        let documents = URL.documentsDirectory
        let marker = documents.appendingPathComponent("dearby-seed-demo-calendar")
        guard FileManager.default.fileExists(atPath: marker.path) else {
            throw XCTSkip("No explicit Simulator demo calendar request")
        }
        try FileManager.default.removeItem(at: marker)
        let store = EKEventStore()
        guard EKEventStore.authorizationStatus(for: .event) == .fullAccess else {
            XCTFail("Explicit calendar permission required"); return
        }
        let receipt = documents.appendingPathComponent("dearby-demo-calendar-id.txt")
        let previous = try? String(contentsOf: receipt, encoding: .utf8)
        let calendar: EKCalendar
        if let previous, let existing = store.calendar(withIdentifier: previous) {
            calendar = existing
        } else {
            calendar = EKCalendar(for: .event, eventStore: store)
            calendar.title = "Dearby 데모 · 삭제 가능"
            calendar.source = try XCTUnwrap(store.sources.first { $0.sourceType == .local })
            try store.saveCalendar(calendar, commit: true)
            try calendar.calendarIdentifier.write(to: receipt, atomically: true, encoding: .utf8)
        }
        let formatter = ISO8601DateFormatter()
        let start = try XCTUnwrap(formatter.date(from: "2026-09-30T14:00:00+09:00"))
        let end = start.addingTimeInterval(3 * 3600)
        let predicate = store.predicateForEvents(withStart: start, end: end, calendars: [calendar])
        let existing = store.events(matching: predicate)
        let fixtures: [(String, TimeInterval, TimeInterval)] = [
            ("[Dearby 데모] 팀 미팅", 1800, 3600),
            ("[Dearby 데모] 개인 약속", 5400, 9000)
        ]
        for (title, begin, finish) in fixtures where !existing.contains(where: { $0.title == title && $0.startDate == start.addingTimeInterval(begin) }) {
            let event = EKEvent(eventStore: store)
            event.calendar = calendar; event.title = title
            event.startDate = start.addingTimeInterval(begin)
            event.endDate = start.addingTimeInterval(finish)
            event.timeZone = TimeZone(identifier: "Asia/Seoul")
            event.notes = "사용자 요청으로 생성한 임시 데모 일정. Dearby 데모 캘린더를 삭제하면 함께 제거됩니다."
            try store.save(event, span: .thisEvent, commit: true)
        }
        let adapter = DeviceCalendarStore()
        let busy = try await adapter.busy(start: start, end: start.addingTimeInterval(7200), calendarIDs: [calendar.calendarIdentifier])
        XCTAssertEqual(busy.count, 2)
        #else
        throw XCTSkip("Simulator only")
        #endif
    }
    private func schedule(start: String? = "2026-10-24T14:00:00+09:00", end: String? = "2026-10-24T16:00:00+09:00", zone: String = "Asia/Seoul") -> ActivityScheduleModel {
        .init(id: "s", title: "검증 활동", startAt: start, endAt: end, dateLabel: "", timeZone: zone)
    }
    func testHalfOpenOverlapAndIntersection() throws {
        let window = try XCTUnwrap(CalendarWindowState.parse(schedule()))
        XCTAssertFalse(CalendarOverlapState.matches(window, .init(start: window.end, end: window.end.addingTimeInterval(60))))
        XCTAssertFalse(CalendarOverlapState.matches(window, .init(start: window.start.addingTimeInterval(-60), end: window.start)))
        XCTAssertFalse(CalendarOverlapState.matches(window, .init(start: window.start, end: window.start)))
        let busy = DeviceBusyTime(start: window.start.addingTimeInterval(3600), end: window.end.addingTimeInterval(3600))
        XCTAssertTrue(CalendarOverlapState.matches(window, busy))
        let item = CalendarOverlapState(id: 0, activity: window, busy: busy)
        XCTAssertEqual(item.start, busy.start)
        XCTAssertEqual(item.end, window.end)
    }
    func testMissingInvalidAndPartialTimesRemainUnknown() async {
        for value in [schedule(start: nil), schedule(end: nil), schedule(end: "invalid"),
                      schedule(end: "2026-10-24T13:00:00+09:00"), schedule(zone: "invalid")] {
            XCTAssertNil(CalendarWindowState.parse(value))
        }
        let state = CalendarConflictState(schedules: [schedule(start: nil)])
        await state.connect(request: false)
        XCTAssertEqual(state.phase, .unknown)
        XCTAssertTrue(state.calendars.isEmpty)
        XCTAssertTrue(state.overlaps.isEmpty)
        let partial = CalendarConflictState(schedules: [schedule(), schedule(end: nil)])
        XCTAssertEqual(partial.windows.count, 1)
        XCTAssertEqual(partial.unknownCount, 1)
        partial.clear()
        XCTAssertEqual(partial.phase, .idle)
        XCTAssertTrue(partial.selected.isEmpty)
    }
    func testDeniedPermissionDoesNotBecomeEmptySuccess() async throws {
        guard EKEventStore.authorizationStatus(for: .event) != .fullAccess else {
            throw XCTSkip("Run after revoking permission on the dedicated Simulator")
        }
        let state = CalendarConflictState(schedules: [schedule()])
        await state.connect(request: false)
        XCTAssertEqual(state.phase, .permission)
        XCTAssertTrue(state.overlaps.isEmpty)
    }
    func testFreeAndCancelledAreExcludedButUnknownIsConservative() {
        XCTAssertFalse(DeviceCalendarStore.blocksTime(status: .confirmed, availability: .free))
        XCTAssertFalse(DeviceCalendarStore.blocksTime(status: .canceled, availability: .busy))
        XCTAssertTrue(DeviceCalendarStore.blocksTime(status: .tentative, availability: .tentative))
        XCTAssertTrue(DeviceCalendarStore.blocksTime(status: .none, availability: .notSupported))
    }
    func testOffsetsAndDSTUseAbsoluteInstants() throws {
        let value = try XCTUnwrap(CalendarWindowState.parse(schedule(start: "2026-11-01T01:30:00-04:00", end: "2026-11-01T01:30:00-05:00", zone: "America/New_York")))
        XCTAssertEqual(value.end.timeIntervalSince(value.start), 3600)
    }
    func testDiscoveryPreservesExistingBookmarks() throws {
        let store = try LocalStore(container: ModelContainer(for: StoredDocument.self, configurations: ModelConfiguration(isStoredInMemoryOnly: true)))
        let state = try DiscoveryState(store: store, api: APIClient(baseURL: nil))
        try state.catalog.toggleProgram("preserved-program")
        let reloaded = try DiscoveryState(store: store, api: APIClient(baseURL: nil))
        XCTAssertTrue(reloaded.catalog.local.programIDs.contains("preserved-program"))
    }
    // Opt in by granting Calendar to the app on a dedicated Simulator first.
    // Never creates or modifies events in a user's calendar: only a new temporary calendar.
    func testRealEventKitRecurringBusyFreeAndPrivacyClear() async throws {
        guard ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Dearby-Calendar") == true,
              EKEventStore.authorizationStatus(for: .event) == .fullAccess else {
            throw XCTSkip("Requires dedicated Dearby-Calendar Simulator and explicit calendar grant")
        }
        let store = EKEventStore()
        let calendar = EKCalendar(for: .event, eventStore: store)
        calendar.title = "Dearby test " + UUID().uuidString
        calendar.source = try XCTUnwrap(store.sources.first { $0.sourceType == .local })
        try store.saveCalendar(calendar, commit: true)
        defer { try? store.removeCalendar(calendar, commit: true) }
        let window = try XCTUnwrap(CalendarWindowState.parse(schedule()))
        let event = EKEvent(eventStore: store)
        event.calendar = calendar; event.title = "PRIVATE TEST TITLE MUST NOT LEAVE ADAPTER"
        event.startDate = window.start.addingTimeInterval(-86_400 + 1800)
        event.endDate = window.start.addingTimeInterval(-86_400 + 5400)
        event.availability = .busy
        event.recurrenceRules = [EKRecurrenceRule(recurrenceWith: .daily, interval: 1, end: EKRecurrenceEnd(occurrenceCount: 2))]
        try store.save(event, span: .thisEvent, commit: true)
        let free = EKEvent(eventStore: store)
        free.calendar = calendar; free.title = "FREE TEST"
        free.startDate = window.start; free.endDate = window.end; free.availability = .free
        if calendar.supportedEventAvailabilities.contains(.free) {
            try store.save(free, span: .thisEvent, commit: true)
        }
        let adapter = DeviceCalendarStore()
        let choices = try await adapter.calendars(request: false)
        XCTAssertTrue(choices.contains { $0.id == calendar.calendarIdentifier })
        let times = try await adapter.busy(start: window.start, end: window.end, calendarIDs: [calendar.calendarIdentifier])
        XCTAssertEqual(times.count, 1)
        XCTAssertEqual(times.first?.start, window.start.addingTimeInterval(1800))
        let state = CalendarConflictState(schedules: [schedule()], store: adapter)
        await state.connect(request: false)
        for choice in state.calendars where choice.id != calendar.calendarIdentifier { state.toggle(choice.id) }
        await state.compare()
        XCTAssertEqual(state.phase, .result)
        XCTAssertEqual(state.overlaps.count, 1)
        state.clear()
        XCTAssertTrue(state.overlaps.isEmpty)
        XCTAssertTrue(state.calendars.isEmpty)
    }
}
