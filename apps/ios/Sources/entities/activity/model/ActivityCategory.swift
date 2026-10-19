import Foundation

/// 활동 종류 (contract "활동 종류(category) — 2026-10-19"): the API's upper-case code with its Korean label.
/// Cases are in the contract's display order; an unknown or missing code reads as 미분류 (nil).
enum ActivityCategory: String, CaseIterable, Sendable {
    case bootcamp = "BOOTCAMP", conference = "CONFERENCE", workshop = "WORKSHOP", study = "STUDY", club = "CLUB", lecture = "LECTURE"
    case hackathon = "HACKATHON", contest = "CONTEST", meetup = "MEETUP", other = "OTHER"
    var label: String {
        switch self {
        case .bootcamp: "부트캠프"
        case .conference: "컨퍼런스"
        case .workshop: "워크샵"
        case .study: "스터디"
        case .club: "동아리"
        case .lecture: "강연"
        case .hackathon: "해커톤"
        case .contest: "공모전"
        case .meetup: "밋업"
        case .other: "기타"
        }
    }
    static let unclassifiedLabel = "미분류"
    /// Activities grouped in contract order, 미분류 last; one entry per category present. Inside a group the nearest
    /// recruitment deadline comes first, activities without one last, ties by title.
    static func group(_ activities: [ActivityModel]) -> [(label: String, activities: [ActivityModel])] {
        let known = allCases.compactMap { category in
            let members = activities.filter { $0.category == category }
            return members.isEmpty ? nil : (label: category.label, activities: byDeadline(members))
        }
        let unclassified = activities.filter { $0.category == nil }
        return unclassified.isEmpty ? known : known + [(label: unclassifiedLabel, activities: byDeadline(unclassified))]
    }
    private static func byDeadline(_ activities: [ActivityModel]) -> [ActivityModel] {
        activities.sorted { lhs, rhs in
            switch (lhs.recruitmentDeadline, rhs.recruitmentDeadline) {
            case let (left?, right?) where left != right: left < right
            case (nil, .some): false
            case (.some, nil): true
            default: lhs.title < rhs.title
            }
        }
    }
}
