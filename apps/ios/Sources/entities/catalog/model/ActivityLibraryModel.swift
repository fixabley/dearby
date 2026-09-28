import Foundation

struct ActivityLibraryModel: Codable, Equatable, Sendable {
    enum ApplicationStatus: String, Codable, Sendable { case applied, notApplied }
    var programIDs: Set<String> = []
    var organizationIDs: Set<String> = []
    var applications: [String: ApplicationStatus] = [:]
}
