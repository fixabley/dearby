import SwiftUI

/// Location summary and explicit per-venue actions; no OS services in this slice.
struct NoticeLocationView: View {
    let location: NoticeLocation
    let onOpenMap: (NoticeVenue) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            InformationRow(title: "활동 장소", value: location.summary)
            if !location.venuesWithCoordinates.isEmpty {
                Text("층·호실은 장소 안내를 확인해 주세요.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            ForEach(Array(location.venuesWithCoordinates.enumerated()), id: \.offset) { _, venue in
                VenueMapButton(venue: venue, onOpenMap: onOpenMap)
            }
        }
    }
}
