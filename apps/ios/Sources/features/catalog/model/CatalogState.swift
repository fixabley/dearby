import Foundation
import Observation

@MainActor @Observable final class CatalogState {
    private let store: LocalStore
    private let api: APIClient
    private let cacheKey: String
    private(set) var catalog: CatalogModel?
    private(set) var local: ActivityLibraryModel
    private(set) var loading = false
    private(set) var fromCache = false
    private(set) var error: String?
    private(set) var fetchedAt: Date?

    init(store: LocalStore, api: APIClient) throws {
        self.store = store
        self.api = api
        cacheKey = "catalog.v1." + (api.baseURL?.absoluteString ?? "unconfigured")
        local = try store.read(ActivityLibraryModel.self, key: "activity.library.v1") ?? ActivityLibraryModel()
        do {
            let cached = try store.read(CatalogCacheModel.self, key: cacheKey)
            try cached?.catalog.validate()
            catalog = cached?.catalog
            fetchedAt = cached?.fetchedAt
            fromCache = cached != nil
        } catch {
            self.error = "보관한 활동 정보를 읽지 못했어요. 다시 불러와 주세요."
        }
    }
    // Wake at semantic boundaries rather than redrawing the list every second.
    var expirationDates: [Date] {
        let now = Date()
        let dates = (catalog?.activities ?? []).flatMap { activity in
            [CatalogModel.date(activity.validUntil), CatalogModel.date(activity.recruitmentStartAt),
             CatalogModel.date(activity.recruitmentEndAt),
             CatalogModel.date(activity.sourceCheckedAt)?.addingTimeInterval(86_400)].compactMap { $0 }
        }
        return [now] + Set(dates.filter { $0 > now }).sorted()
    }
    func refresh() async {
        guard !loading else { return }
        loading = true
        error = nil
        defer { loading = false }
        do {
            let response: CatalogModel = try await api.request("GET", "catalog")
            try Task.checkCancellation()
            try response.validate()
            let now = Date()
            try store.write(CatalogCacheModel(catalog: response, fetchedAt: now), key: cacheKey)
            catalog = response
            fetchedAt = now
            fromCache = false
        } catch is CancellationError {
            fromCache = catalog != nil
        } catch {
            self.error = "새로고침 실패 · \(error.localizedDescription)"
            fromCache = catalog != nil
        }
    }
    func toggleProgram(_ id: String) throws {
        var next = local
        if !next.programIDs.insert(id).inserted { next.programIDs.remove(id) }
        try commit(next)
    }
    func toggleOrganization(_ id: String) throws {
        var next = local
        if !next.organizationIDs.insert(id).inserted { next.organizationIDs.remove(id) }
        try commit(next)
    }
    func report(_ status: ActivityLibraryModel.ApplicationStatus, activityID: String) throws {
        var next = local
        next.applications[activityID] = status
        try commit(next)
    }
    private func commit(_ next: ActivityLibraryModel) throws {
        try store.write(next, key: "activity.library.v1")
        local = next
    }
}
private struct CatalogCacheModel: Codable {
    let catalog: CatalogModel
    let fetchedAt: Date
}
