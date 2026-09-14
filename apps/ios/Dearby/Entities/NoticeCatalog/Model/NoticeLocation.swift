struct NoticeLocation: Decodable {
    let summary: String
    let mode: String
    let status: String
    let venues: [NoticeVenue]

    var venuesWithCoordinates: [NoticeVenue] {
        guard mode != "online" else { return [] }
        return venues.filter { $0.coordinates != nil }
    }
}
