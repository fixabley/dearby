import Foundation

struct NoticeCardScheduleState: Identifiable {
    let id: Int
    let title: String
    let period: String
    let places: [NoticeCardPlaceState]
}

struct NoticeCardPlaceState: Identifiable {
    let id: Int
    let text: String
    let venueIndex: Int?
}
