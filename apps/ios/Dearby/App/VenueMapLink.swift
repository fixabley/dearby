import Foundation

/// Apple Maps request construction belongs to App, not the catalog entity.
enum VenueMapLink {
    static func url(for venue: NoticeVenue) -> URL? {
        guard let coordinates = venue.coordinates else { return nil }
        var components = URLComponents()
        components.scheme = "https"
        components.host = "maps.apple.com"
        components.path = "/"
        components.queryItems = [
            URLQueryItem(name: "ll", value: "\(coordinates.latitude),\(coordinates.longitude)"),
            URLQueryItem(name: "q", value: venue.name)
        ]
        return components.url
    }
}
