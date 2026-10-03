import Observation

@MainActor @Observable final class CatalogViewModel {
    let activities = DemoActivities.all
    var appliedIDs: Set<String> = []
}
