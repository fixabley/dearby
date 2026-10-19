import Foundation

/// One entry of the operator-managed tag vocabulary (contract "태그(얻을 수 있는 것) — 2026-10-19"): what a participant gains,
/// such as 창업 or 멘토링. `nameEn` may be empty until an operator fills it.
struct Tag: Equatable, Hashable, Identifiable, Sendable {
    let id: Int64
    let name: String
    var nameEn: String? = nil
}
