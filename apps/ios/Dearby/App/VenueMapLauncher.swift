import Foundation

@MainActor
enum VenueMapLauncher {
    static func open(_ venue: ActivityVenue,
                     using openURL: (URL, @escaping (Bool) -> Void) -> Void,
                     onFailure: @escaping () -> Void) {
        guard let url = VenueMapLink.url(for: venue) else {
            onFailure()
            return
        }
        // Universal map links let the OS choose Maps or its web fallback.
        openURL(url) { accepted in
            if !accepted { onFailure() }
        }
    }
}
