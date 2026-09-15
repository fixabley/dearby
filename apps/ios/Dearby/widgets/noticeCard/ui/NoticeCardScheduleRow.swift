import SwiftUI

struct NoticeCardScheduleRow: View {
    let state: NoticeCardScheduleState
    let onOpenMap: (Int) -> Void
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let layout = typeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
            : AnyLayout(HStackLayout(alignment: .top, spacing: 10))
        layout {
            Text(state.title)
                .font(.subheadline.weight(.semibold))
                .frame(width: typeSize.isAccessibilitySize ? nil : 68, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .top, spacing: 4) {
                    Image(systemName: "calendar").foregroundStyle(.secondary)
                        .font(.system(size: 17)).frame(width: 20).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 3) {
                        ForEach(state.period.indices, id: \.self) { index in
                            Text(state.period[index]).fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                ForEach(state.places) { place in
                    HStack(alignment: .top, spacing: 4) {
                        Image(systemName: "mappin.and.ellipse").foregroundStyle(.secondary)
                            .font(.system(size: 17)).frame(width: 20).accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 3) {
                            ForEach(place.fields.indices, id: \.self) { index in
                                NoticeCardPlaceText(text: place.fields[index])
                            }
                        }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .fixedSize(horizontal: false, vertical: true)
                        if let index = place.venueIndex {
                            Button { onOpenMap(index) } label: {
                                Image(systemName: "map").frame(minWidth: 44, minHeight: 44)
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
