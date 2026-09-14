struct ActivityLocation: Decodable {
    let summary: String
    let mode: String
    let status: String
    let venues: [ActivityVenue]

    var venuesWithCoordinates: [ActivityVenue] {
        venues.filter { $0.coordinates != nil }
    }
}
