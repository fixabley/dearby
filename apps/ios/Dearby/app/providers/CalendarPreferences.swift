import Foundation
import Observation

/// App-scoped preference/permission owner; personal results stay in weakly attached detail sessions.
@MainActor @Observable final class CalendarPreferences {
    private(set) var enabled: Bool
    private(set) var firstPromptHandled: Bool
    private(set) var showFirstPrompt = false
    private(set) var connection: CalendarConnectionState = .off
    @ObservationIgnored let provider: any BusyCalendarProvider
    @ObservationIgnored private let store: any CalendarPreferenceStore
    @ObservationIgnored private var operation: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    private struct Observer { weak var session: BusyCalendarSession? }
    @ObservationIgnored private var observers: [ObjectIdentifier: Observer] = [:]
    var switchIsOn: Bool { enabled || connection == .checking || connection == .requesting }

    init(store: any CalendarPreferenceStore, provider: any BusyCalendarProvider) {
        self.store = store; self.provider = provider
        enabled = store.enabled; firstPromptHandled = store.firstPromptHandled
        connection = enabled ? .connected : .off
    }
    deinit { operation?.cancel() }
    func start() {
        if !firstPromptHandled { showFirstPrompt = true }
        if enabled { refreshAuthorization() }
    }
    func later() {
        showFirstPrompt = false; firstPromptHandled = true; store.firstPromptHandled = true
        setEnabled(false)
    }
    func enableFromFirstPrompt() {
        showFirstPrompt = false; firstPromptHandled = true; store.firstPromptHandled = true
        setEnabled(true)
    }
    func setEnabled(_ value: Bool) {
        invalidate()
        if !value { publish(false, state: .off); return }
        connection = .checking
        let token = generation
        operation = Task { [weak self, provider] in
            var access = await provider.authorization()
            guard let self, self.current(token) else { return }
            do {
                if access == .notRequested {
                    self.connection = .requesting
                    access = try await provider.requestReadPermission()
                }
                guard self.current(token) else { return }
                self.accept(access)
            } catch {
                guard self.current(token) else { return }
                self.publish(false, state: .failed)
            }
        }
    }
    func attach(_ session: BusyCalendarSession) {
        observers[ObjectIdentifier(session)] = Observer(session: session)
        if enabled { session.setEnabled(true) }
    }
    func detach(_ session: BusyCalendarSession) {
        observers.removeValue(forKey: ObjectIdentifier(session)); session.close()
    }
    func refreshAuthorization() {
        guard enabled, connection != .requesting else { return }
        invalidate(); let token = generation
        operation = Task { [weak self, provider] in
            let access = await provider.authorization()
            guard let self, self.current(token) else { return }
            self.accept(access)
        }
    }
    func lifecycle(_ phase: BusyCalendarLifecycle) {
        switch phase {
        case .inactive: break // OS permission dialog does not cancel its own request.
        case .active: refreshAuthorization()
        case .background:
            invalidate()
            if !enabled { connection = .off }
            for observer in observers.values { observer.session?.suspend() }
            Task { [provider] in await provider.discard() }
        }
    }
    private func accept(_ access: BusyCalendarAuthorization) {
        switch access {
        case .fullAccess: publish(true, state: .connected)
        case .restricted: publish(false, state: .restricted)
        default: publish(false, state: .denied)
        }
    }
    private func publish(_ value: Bool, state: CalendarConnectionState) {
        enabled = value; store.enabled = value; connection = state
        observers = observers.filter { $0.value.session != nil }
        for observer in observers.values {
            if value {
                observer.session?.resume()
                observer.session?.setEnabled(true)
            } else {
                observer.session?.setEnabled(false)
            }
        }
        if !value { Task { [provider] in await provider.discard() } }
    }
    private func invalidate() { generation += 1; operation?.cancel(); operation = nil }
    private func current(_ token: Int) -> Bool { generation == token && !Task.isCancelled }
}
