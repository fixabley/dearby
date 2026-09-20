import Foundation
import Observation

enum BusyCalendarLifecycle { case active, inactive, background }

/// One opt-in owner per detail presentation; query tasks and personal results are ephemeral.
@MainActor @Observable
final class BusyCalendarSession {
    private(set) var isEnabled = false
    private(set) var connection: CalendarConnectionState = .off
    private(set) var days: [Int: BusyTimeDisplay] = [:]
    @ObservationIgnored private let provider: any BusyCalendarProvider
    @ObservationIgnored private var operation: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var suspended = false
    private struct Selection: Equatable { let day: DateInterval; let activity: DateInterval }
    @ObservationIgnored private var selections: [Int: Selection] = [:]

    init(provider: any BusyCalendarProvider) { self.provider = provider }
    deinit { operation?.cancel() }

    func setEnabled(_ value: Bool) {
        isEnabled = value
        if !value { invalidate(); connection = .off; discard(); return }
        suspended = false
        connection = .checking
        refresh()
    }
    func select(id: Int, day: DateInterval, activity: DateInterval) {
        guard day.duration > 0, activity.duration > 0,
              max(day.start, activity.start) < min(day.end, activity.end) else { return }
        let value = Selection(day: day, activity: activity)
        guard selections[id] != value else { return }
        selections[id] = value
        if isEnabled && connection == .connected { refresh() }
    }
    func refresh() {
        guard isEnabled, !suspended else { return }
        invalidate()
        let token = generation, requests = selections
        for id in requests.keys { days[id] = .loading }
        operation = Task { [weak self, provider] in
            let access = await provider.authorization()
            guard let self, self.current(token), self.isEnabled else { return }
            guard access == .fullAccess else {
                self.disconnect(access == .restricted ? .restricted : .denied); return
            }
            self.connection = .connected
            for (id, selection) in requests.sorted(by: { $0.key < $1.key }) {
                guard self.current(token), self.isEnabled else { return }
                do {
                    let fetched = try await provider.intervals(in: selection.day)
                    let access = await provider.authorization()
                    guard self.current(token), self.isEnabled else { return }
                    guard access == .fullAccess else {
                        self.disconnect(access == .restricted ? .restricted : .denied); return
                    }
                    let intervals = BusyTimeInterval.merged(fetched, in: selection.day)
                    self.days[id] = BusyTimeDisplay(status: .ready, intervals: intervals,
                        overlaps: BusyTimeInterval.merged(intervals, in: selection.activity))
                } catch {
                    guard self.current(token), self.isEnabled else { return }
                    let access = await provider.authorization()
                    guard self.current(token) else { return }
                    if access != .fullAccess { self.disconnect(access == .restricted ? .restricted : .denied); return }
                    self.days[id] = .failed
                }
            }
        }
    }
    func suspend() {
        suspended = true; invalidate(); discard()
    }
    func close() {
        isEnabled = false; connection = .off; selections.removeAll(); invalidate(); discard()
    }
    private func current(_ token: Int) -> Bool { generation == token && !Task.isCancelled && !suspended }
    private func invalidate() { generation += 1; operation?.cancel(); operation = nil; days.removeAll() }
    private func disconnect(_ state: CalendarConnectionState) { isEnabled = false; invalidate(); connection = state; discard() }
    private func discard() { Task { [provider] in await provider.discard() } }
}
