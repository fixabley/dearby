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
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.headline).frame(maxWidth: .infinity, minHeight: 50)
            .foregroundStyle(.white)
            .background(DearbyStyle.teal, in: RoundedRectangle(cornerRadius: 11))
            .opacity(!isEnabled ? 0.45 : (configuration.isPressed ? 0.7 : 1))
    }
}
