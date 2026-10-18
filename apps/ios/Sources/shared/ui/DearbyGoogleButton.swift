import SwiftUI

/// "Google로 계속하기" in Google's light button shape (white, thin grey border), as tall as `DearbyButtonStyle`.
/// The leading "G" is a text placeholder until the official logo file lands in `shared/assets/prototype`
/// (contract "구글 로그인": the asset is applied once downloaded; no drawn imitation of the logo).
struct DearbyGoogleButton: View {
    let action: () -> Void
    @Environment(\.isEnabled) private var isEnabled
    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Text("G").font(.system(size: 18, weight: .bold, design: .rounded)).foregroundStyle(DearbyStyle.ink)
                    .accessibilityHidden(true)
                Text("Google로 계속하기").font(.dearby(.headline))
            }
            .foregroundStyle(DearbyStyle.ink).frame(maxWidth: .infinity, minHeight: 50)
            .background(.white, in: RoundedRectangle(cornerRadius: 11))
            .overlay(RoundedRectangle(cornerRadius: 11).stroke(DearbyStyle.line, lineWidth: 1))
            .opacity(isEnabled ? 1 : 0.45)
        }
        .buttonStyle(.plain).accessibilityLabel("Google로 계속하기")
    }
}
