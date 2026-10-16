import AuthenticationServices
import SwiftUI

/// Apple's own "Sign in with Apple" button (HIG: black on light, white on dark), as tall as `DearbyButtonStyle`.
/// `SignInWithAppleButton` builds its request synchronously, but the request needs a server nonce first,
/// so this button only reports the tap and the caller runs the request.
struct DearbyAppleButton: View {
    let action: () -> Void
    @Environment(\.colorScheme) private var colorScheme
    var body: some View {
        // The button's style is fixed when it is made, so a color scheme change makes a new one.
        AppleIDButton(style: colorScheme == .dark ? .white : .black, action: action)
            .id(colorScheme).frame(maxWidth: .infinity, minHeight: 50, maxHeight: 50)
    }
}

private struct AppleIDButton: UIViewRepresentable {
    let style: ASAuthorizationAppleIDButton.Style
    let action: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator() }
    func makeUIView(context: Context) -> ASAuthorizationAppleIDButton {
        let button = ASAuthorizationAppleIDButton(type: .continue, style: style)
        button.cornerRadius = 11
        button.addTarget(context.coordinator, action: #selector(Coordinator.tapped), for: .touchUpInside)
        return button
    }
    func updateUIView(_ button: ASAuthorizationAppleIDButton, context: Context) {
        context.coordinator.action = action
        button.isEnabled = context.environment.isEnabled
        button.alpha = context.environment.isEnabled ? 1 : 0.45
    }

    @MainActor final class Coordinator: NSObject {
        var action: () -> Void = {}
        @objc func tapped() { action() }
    }
}
