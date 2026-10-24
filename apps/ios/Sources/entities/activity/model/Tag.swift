import Foundation

/// One entry of the operator-managed tag vocabulary (contract "분야·성격 태그와 아이콘" 2026-10-24): a 분야 (`field`, what the activity is
/// about, such as 데이터·AI) or a 성격 (`trait`, what a participant gains, such as 멘토링). `nameEn` picks the icon and may be empty.
struct Tag: Equatable, Hashable, Identifiable, Sendable {
    enum Kind: String, Sendable { case field = "FIELD", trait = "TRAIT" }
    let id: Int64
    let name: String
    var nameEn: String? = nil
    /// Older servers send no kind; such a tag is a 성격.
    var kind: Kind = .trait
}
