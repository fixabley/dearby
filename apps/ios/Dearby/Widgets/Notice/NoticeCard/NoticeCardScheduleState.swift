import Foundation

struct NoticeCardScheduleState: Identifiable {
    let id: Int
    let title: String
    let period: [String]
    let places: [NoticeCardPlaceState]
}

struct NoticeCardPlaceState: Identifiable {
    let id: Int
    let fields: [String]
    var text: String { fields.joined(separator: " · ") }
    let venueIndex: Int?
}
