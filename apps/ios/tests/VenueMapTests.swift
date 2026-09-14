import Foundation

@main
struct VenueMapTests {
    @MainActor
    static func main() throws {
        let decoder = JSONDecoder()
        decoder.nonConformingFloatDecodingStrategy = .convertFromString(positiveInfinity: "Infinity", negativeInfinity: "-Infinity", nan: "NaN")
        func venue(_ coordinates: String?) throws -> ActivityVenue {
            let coordinateField = coordinates.map { ",\"coordinates\":\($0)" } ?? ""
            let json = "{\"phase\":\"event\",\"name\":\"한국 & 도서관 #5층\",\"address\":null\(coordinateField)}"
            return try decoder.decode(ActivityVenue.self, from: Data(json.utf8))
        }
        let invalid = [nil, "null", "{}", "{\"latitude\":37}", "{\"longitude\":127}",
                       "{\"latitude\":91,\"longitude\":0}", "{\"latitude\":0,\"longitude\":181}",
                       "{\"latitude\":-91,\"longitude\":0}", "{\"latitude\":0,\"longitude\":-181}",
                       "{\"latitude\":\"NaN\",\"longitude\":0}", "{\"latitude\":0,\"longitude\":\"Infinity\"}",
                       "{\"latitude\":\"37\",\"longitude\":127}"] as [String?]
        var invalidVenues: [ActivityVenue] = []
        for value in invalid {
            let item = try venue(value)
            precondition(item.coordinates == nil && item.name == "한국 & 도서관 #5층")
            precondition(VenueMapLink.url(for: item) == nil)
            invalidVenues.append(item)
        }
        precondition(ActivityCoordinates(latitude: .nan, longitude: 0) == nil)
        precondition(ActivityCoordinates(latitude: 0, longitude: -.infinity) == nil)
        let zero = try venue("{\"latitude\":0,\"longitude\":0}")
        let edge = try venue("{\"latitude\":-90,\"longitude\":180}")
        precondition(zero.coordinates == ActivityCoordinates(latitude: 0, longitude: 0))
        precondition(edge.coordinates != nil)
        precondition(ActivityCoordinates(latitude: 90, longitude: -180) != nil)
        let location = ActivityLocation(summary: "본관 5층 / 별관", mode: "offline", status: "known", venues: invalidVenues + [zero, edge])
        precondition(location.venuesWithCoordinates.count == 2)
        precondition(location.venuesWithCoordinates.map(\.coordinates) == [zero.coordinates, edge.coordinates])
        precondition(ActivityLocation(summary: "온라인", mode: "online", status: "unknown", venues: invalidVenues).venuesWithCoordinates.isEmpty)
        let url = VenueMapLink.url(for: zero)!
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)!
        precondition(components.scheme == "https" && components.host == "maps.apple.com" && components.fragment == nil)
        precondition(components.queryItems == [URLQueryItem(name: "ll", value: "0.0,0.0"), URLQueryItem(name: "q", value: zero.name)])
        precondition(url.absoluteString.contains("%26") && url.absoluteString.contains("%23"))
        var requests: [URL] = []
        var failures = 0
        VenueMapLauncher.open(zero, using: { request, completion in requests.append(request); completion(true) }, onFailure: { failures += 1 })
        precondition(requests == [url] && failures == 0)
        VenueMapLauncher.open(edge, using: { request, completion in requests.append(request); completion(false) }, onFailure: { failures += 1 })
        precondition(requests.last == VenueMapLink.url(for: edge) && failures == 1)
        VenueMapLauncher.open(invalidVenues[0], using: { _, _ in preconditionFailure("Unknown coordinate must not launch") }, onFailure: { failures += 1 })
        precondition(failures == 2)
        print("PASS: missing/null/partial/invalid/nonfinite/range/zero coordinates, multiple venue controls, Korean &/# map label, exact launch request and failure feedback")
    }
}
