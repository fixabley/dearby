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
    /// SF Symbol (contract "아이콘 통일" 의미별 표의 활동 종류, 2026-10-26): the program card's 종류 줄 and the detail's 종류 칩.
    var systemImage: String {
        switch self {
        case .bootcamp: "graduationcap"
        case .conference: "person.3"
        case .workshop: "pencil.and.ruler"
        case .study: "books.vertical"
        case .club: "person.2"
        case .lecture: "mic"
        case .hackathon: "keyboard"
        case .contest: "trophy"
        case .meetup: "cup.and.saucer"
        case .other: "square.grid.2x2"
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

/// 발견 `프로그램 | 이벤트` (user 2026-10-26): `GET /v1/discovery/programs?kind=`. 이벤트 is 컨퍼런스·밋업·강연·워크샵; 프로그램 is every other
/// 종류 and 미분류 (해커톤 included). The server judges it; a server without `kind` ignores it and lists everything.
enum DiscoveryKind: String, CaseIterable, Sendable {
    case program, event
    var title: String { self == .program ? "프로그램" : "이벤트" }
    /// 종류 필터의 항목 (contract "종류 필터" 2026-10-26): this side's 종류 in contract order (the server's 이벤트 set is
    /// 컨퍼런스·워크샵·강연·밋업, everything else is 프로그램).
    var categories: [ActivityCategory] {
        ActivityCategory.allCases.filter { [.conference, .workshop, .lecture, .meetup].contains($0) == (self == .event) }
    }
    /// The 종류 button's summary: `전체` for none, up to two names joined, else `부트캠프 외 2`.
    func summary(_ chosen: Set<ActivityCategory>) -> String {
        let names = categories.filter(chosen.contains).map(\.label)
        if names.isEmpty { return "전체" }
        return names.count <= 2 ? names.joined(separator: "·") : "\(names[0]) 외 \(names.count - 1)"
    }
}
