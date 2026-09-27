import Foundation

struct OrganizationModel: Codable, Equatable, Identifiable, Sendable {
    let id: String
    let name: String
    let description: String
}
struct ProgramModel: Codable, Equatable, Identifiable, Sendable {
    let id: String
    let organizationId: String
    let title: String
    let description: String
}
struct CatalogModel: Codable, Equatable, Sendable {
    let generatedAt: String
    let organizations: [OrganizationModel]
    let programs: [ProgramModel]
    let activities: [ActivityModel]

    func validate() throws {
        func unique(_ ids: [String]) -> Bool {
            ids.allSatisfy { UUID(uuidString: $0) != nil } && Set(ids).count == ids.count
        }
        let organizations = Set(organizations.map(\.id))
        let programs = Dictionary(grouping: programs, by: \.id)
        guard Self.date(generatedAt) != nil, unique(self.organizations.map(\.id)),
              unique(self.programs.map(\.id)), unique(activities.map(\.id)),
              self.programs.allSatisfy({ organizations.contains($0.organizationId) }),
              activities.allSatisfy({ activity in
                  programs[activity.programId]?.first?.organizationId == activity.organizationId
                      && organizations.contains(activity.organizationId) && unique(activity.schedules.map(\.id))
              }) else { throw CocoaError(.coderReadCorrupt) }
    }
    static func date(_ raw: String?) -> Date? {
        guard let raw else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: raw) ?? ISO8601DateFormatter().date(from: raw)
    }
}
