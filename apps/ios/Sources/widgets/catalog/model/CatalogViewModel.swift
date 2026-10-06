import Foundation
import Observation

@MainActor @Observable final class CatalogViewModel {
    let activities = DemoActivities.all
    var appliedIDs = DemoActivities.appliedIDs
    // The user's own participation mark, not an organizer confirmation.
    var confirmedIDs: Set<String> = []
    var appliedActivities: [ActivityModel] {
        activities.filter { appliedIDs.contains($0.id) }
            .sorted { ($0.schedules.first?.start ?? .distantFuture) < ($1.schedules.first?.start ?? .distantFuture) }
    }
    func apply(_ id: String, _ value: Bool) {
        if value { appliedIDs.insert(id) } else { appliedIDs.remove(id); confirmedIDs.remove(id) }
    }
    func confirm(_ id: String, _ value: Bool) {
        if value && appliedIDs.contains(id) { confirmedIDs.insert(id) } else { confirmedIDs.remove(id) }
    }
}
