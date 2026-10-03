import SwiftUI

enum DearbyStyle {
    static let teal = Color(red: 0, green: 127.0 / 255, blue: 128.0 / 255)
    static let quiet = Color(red: 101.0 / 255, green: 112.0 / 255, blue: 120.0 / 255)
    static let mint = Color(red: 240.0 / 255, green: 252.0 / 255, blue: 250.0 / 255)
    static let line = Color(red: 227.0 / 255, green: 232.0 / 255, blue: 234.0 / 255)
    static let muted = Color(red: 245.0 / 255, green: 247.0 / 255, blue: 248.0 / 255)
    static let ink = Color(red: 23.0 / 255, green: 32.0 / 255, blue: 39.0 / 255)
}

struct DearbyLogo: View {
    var width: CGFloat = 92
    var body: some View {
        Image("DearbyLogo").resizable().scaledToFit().frame(width: width).accessibilityLabel("Dearby")
    }
}

struct DearbyButtonStyle: ButtonStyle {
    var outlined = false
    @Environment(\.isEnabled) private var isEnabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.headline).frame(maxWidth: .infinity, minHeight: 50)
            .foregroundStyle(outlined ? DearbyStyle.teal : .white)
            .background(outlined ? .white : DearbyStyle.teal, in: RoundedRectangle(cornerRadius: 11))
            .overlay(RoundedRectangle(cornerRadius: 11).stroke(DearbyStyle.teal, lineWidth: outlined ? 1 : 0))
            .opacity(!isEnabled ? 0.45 : (configuration.isPressed ? 0.7 : 1))
    }
}
