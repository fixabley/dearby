struct ActivityLocation: Decodable {
    let summary: String
    let mode: String
    let status: String
    let venues: [ActivityVenue]

    var venuesWithCoordinates: [ActivityVenue] {
        guard mode != "online" else { return [] }
        return venues.filter { $0.coordinates != nil }
    }
}
