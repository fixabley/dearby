import SwiftUI

enum DearbyStyle {
    static let teal = Color(red: 0, green: 0.45, blue: 0.45)
    static let quiet = Color(red: 0.396, green: 0.439, blue: 0.471)
    static let mint = Color(red: 0.94, green: 0.99, blue: 0.98)
    static let line = Color(red: 0.87, green: 0.9, blue: 0.91)
    static let muted = Color(red: 0.95, green: 0.96, blue: 0.97)
}

struct DearbyLogo: View {
    var width: CGFloat = 92
    var body: some View {
        Image("DearbyLogo").resizable().scaledToFit().frame(width: width).accessibilityLabel("Dearby")
    }
}

struct DearbyButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    var outlined = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.headline).frame(maxWidth: .infinity, minHeight: 50)
            .foregroundStyle(outlined ? DearbyStyle.teal : .white)
            .background(outlined ? Color.white : DearbyStyle.teal, in: RoundedRectangle(cornerRadius: 11))
            .overlay(RoundedRectangle(cornerRadius: 11).stroke(DearbyStyle.teal, lineWidth: outlined ? 1 : 0))
            .opacity(!isEnabled ? 0.45 : (configuration.isPressed ? 0.7 : 1))
    }
}

struct DearbySegments: View {
    let labels: [String]
    @Binding var selection: Int
    var body: some View {
        HStack(spacing: 0) {
            ForEach(labels.indices, id: \.self) { index in
                Button { selection = index } label: {
                    Text(labels[index]).font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 44).padding(.horizontal, 4)
                        .foregroundStyle(selection == index ? .white : DearbyStyle.quiet)
                        .background(selection == index ? DearbyStyle.teal : .clear, in: RoundedRectangle(cornerRadius: 11))
                }.buttonStyle(.plain).accessibilityAddTraits(selection == index ? .isSelected : [])
            }
        }.background(DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 11))
    }
}
