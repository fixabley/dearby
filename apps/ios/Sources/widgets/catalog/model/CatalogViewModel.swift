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
    func confirm(_ id: String, _ value: Bool) {
        if value && appliedIDs.contains(id) { confirmedIDs.insert(id) } else { confirmedIDs.remove(id) }
    }
}
