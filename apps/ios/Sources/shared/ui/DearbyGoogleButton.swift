import SwiftUI

/// "Google로 계속하기" in Google's light/dark button colors (fill #FFFFFF / #131314, 1pt stroke #747775 / #8E918F, text #1F1F1F / #E3E3E3,
/// branding guidelines 2026-07-07), as tall as a `.controlSize(.large)` system button (50). The 20pt "G" is the official asset
/// (`shared/assets/prototype/google-sign-in`, imageset `GoogleG`); its corners carry the button fill, so the fill must stay exactly these values.
struct DearbyGoogleButton: View {
    let action: () -> Void
    @Environment(\.isEnabled) private var isEnabled
    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image("GoogleG").resizable().frame(width: 20, height: 20).accessibilityHidden(true)
                Text("Google로 계속하기").font(.dearby(.headline))
            }
            .foregroundStyle(Self.text).frame(maxWidth: .infinity, minHeight: 50)
            .background(Self.fill, in: RoundedRectangle(cornerRadius: 11))
            .overlay(RoundedRectangle(cornerRadius: 11).strokeBorder(Self.stroke, lineWidth: 1))
            .opacity(isEnabled ? 1 : 0.45)
        }
        .buttonStyle(.plain).accessibilityLabel("Google로 계속하기")
    }

    private static func hex(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(uiColor: UIColor { trait in
            let v = trait.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat(v >> 16 & 0xFF) / 255, green: CGFloat(v >> 8 & 0xFF) / 255, blue: CGFloat(v & 0xFF) / 255, alpha: 1)
        })
    }
    private static let fill = hex(0xFFFFFF, 0x131314)
    private static let stroke = hex(0x747775, 0x8E918F)
    private static let text = hex(0x1F1F1F, 0xE3E3E3)
}
