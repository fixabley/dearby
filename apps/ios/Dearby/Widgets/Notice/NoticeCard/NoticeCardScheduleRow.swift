import SwiftUI

struct NoticeCardScheduleRow: View {
    let state: NoticeCardScheduleState
    let onOpenMap: (Int) -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text(state.title)
                .font(.subheadline.weight(.semibold))
                .frame(width: 68, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .top, spacing: 4) {
                    Text("📅").accessibilityHidden(true)
                    Text(state.period).fixedSize(horizontal: false, vertical: true)
                }
                ForEach(state.places) { place in
                    HStack(alignment: .top, spacing: 0) {
                        Text("📍").accessibilityHidden(true)
                        Text(place.text)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .fixedSize(horizontal: false, vertical: true)
                        if let index = place.venueIndex {
                            Button { onOpenMap(index) } label: {
                                Text("🗺️").frame(minWidth: 44, minHeight: 44)
                            }
                            .buttonStyle(.borderless)
                            .accessibilityLabel("\(state.title), \(place.text) 지도 열기")
                            .accessibilityIdentifier("card.map.\(state.id).\(index)")
                        }
                    }
                }
            }
            .font(.subheadline)
            .padding(.leading, 10)
            .overlay(alignment: .leading) { Rectangle().fill(.separator).frame(width: 0.5) }
        }
        .accessibilityElement(children: .contain)
    }
}
