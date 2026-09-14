struct ActivityLocation: Decodable {
    let summary: String
    let mode: String
    let status: String
    let venues: [ActivityVenue]
}
