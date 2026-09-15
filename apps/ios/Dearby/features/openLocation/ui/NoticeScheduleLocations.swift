import SwiftUI

/// Keeps phase-specific physical venues separate from the original online destination.
struct NoticeScheduleLocations: View {
    let state: NoticeScheduleState
    let onOpenMap: (NoticeVenue) -> Void

    var body: some View {
        ForEach(Array(state.places.enumerated()), id: \.offset) { _, place in
            LocationInformation(name: place.display.name, detail: place.display.detail) {
                if place.venue.coordinates != nil { VenueMapButton(venue: place.venue, onOpenMap: onOpenMap) }
            }
        }
        if state.places.isEmpty {
            LocationInformation(name: state.location, detail: nil) { EmptyView() }
        }
        if let url = state.onlineURL { ExternalLinkCard(url: url, label: "온라인 장소") }
    }
}
