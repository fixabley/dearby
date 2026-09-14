import SwiftData

/// Disk representation only; domain values never retain managed records.
@Model
final class OrganizationRecord {
    @Attribute(.unique) var id: String
    var name: String
    var parentId: String?
    init(_ value: OrganizationModel) {
        id = value.id
        name = value.name
        parentId = value.parentId
    }
}
