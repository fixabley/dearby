import Observation

@MainActor @Observable final class CatalogViewModel {
    let activities = DemoActivities.all
    var savedIDs: Set<String> = []
    var savedOrganization = false
    func toggleSaved(_ id: String) {
        if !savedIDs.insert(id).inserted { savedIDs.remove(id) }
    }
    var appliedIDs: Set<String> = []
}
