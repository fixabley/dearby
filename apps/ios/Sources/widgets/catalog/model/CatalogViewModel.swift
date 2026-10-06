import Foundation
import Observation

@MainActor @Observable final class CatalogViewModel {
    enum Phase: Equatable { case loading, failed, loaded }
    private(set) var phase = Phase.loading
    private(set) var activities: [ActivityModel] = []
    // Discovery is judged at load time so the list does not change while it is on screen.
    private(set) var loadedAt = Date()
    // Example application marks, memory only and keyed by real activity IDs.
    var appliedIDs: Set<String> = []
    // The user's own participation mark, not an organizer confirmation.
    var confirmedIDs: Set<String> = []
    /// Contract #148 "신청 확인": after an official application link opened, ask once when the app comes back.
    enum ApplyAnswer { case applied, notYet, neverAsk }
    private(set) var asking: ActivityModel?
    private var opened: String?
    private var leftApp = false
    private var neverAsk: Set<String> = []
    private let fetch: @Sendable () async throws -> [ActivityModel]
    init(fetch: @escaping @Sendable () async throws -> [ActivityModel]) { self.fetch = fetch }

    var discoverable: [ActivityModel] { activities.filter { $0.isOpen(at: loadedAt) } }
    var appliedActivities: [ActivityModel] {
        activities.filter { appliedIDs.contains($0.id) }
            .sorted { ($0.schedules.first?.start ?? .distantFuture) < ($1.schedules.first?.start ?? .distantFuture) }
    }
    func load() async {
        phase = .loading
        do {
            activities = try await fetch()
            loadedAt = Date()
            phase = .loaded
        } catch {
            phase = .failed
        }
    }
    func apply(_ id: String, _ value: Bool) {
        if value { appliedIDs.insert(id) } else { appliedIDs.remove(id); confirmedIDs.remove(id) }
    }
    /// An application link for `id` was handed to the browser. Applied or "never ask" activities are not asked.
    func openedApplication(_ id: String) {
        guard !appliedIDs.contains(id), !neverAsk.contains(id) else { return }
        opened = id
        leftApp = false
    }
    /// The app went to the background, e.g. because the browser opened.
    func appLeft() { if opened != nil { leftApp = true } }
    /// The app is active again: ask about the last opened application, once.
    func appReturned() {
        guard leftApp, let id = opened else { return }
        opened = nil
        leftApp = false
        asking = activities.first { $0.id == id }
    }
    func answer(_ answer: ApplyAnswer) {
        guard let id = asking?.id else { return }
        asking = nil
        switch answer {
        case .applied: apply(id, true)
        case .notYet: break
        case .neverAsk: neverAsk.insert(id)
        }
    }
    func confirm(_ id: String, _ value: Bool) {
        if value && appliedIDs.contains(id) { confirmedIDs.insert(id) } else { confirmedIDs.remove(id) }
    }
}
