import SwiftUI

struct VenueMapButton: View {
    let venue: NoticeVenue
    let onOpenMap: (NoticeVenue) -> Void

    var body: some View {
        Button { onOpenMap(venue) } label: {
            Label("\(venue.name) 지도 보기", systemImage: "map")
                .frame(minHeight: 44, alignment: .leading)
        }
        .buttonStyle(.borderless)
        .accessibilityIdentifier("venue-map.\(venue.phase).\(venue.name)")
    }
}
