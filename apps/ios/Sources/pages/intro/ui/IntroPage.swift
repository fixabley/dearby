import AuthenticationServices
import SwiftUI

/// First-launch intro. Signing in is optional (App Store 5.1.1): "둘러보기" always opens the app.
/// "패스키로 로그인" (existing passkey accounts; new accounts start with Apple or Google) reuses the sign-in sheet; closing it after a cancel or failure stays here.
/// The Apple button (when the build enables it) and the Google button (until the server says it is off) sign in here;
/// a first-time Apple or Google account opens the sheet's new-or-link choice.
/// Layout is the centred message (contract "비스크롤 화면 배치·CTA") with the lockup as its glyph; the buttons stack at the bottom.
struct IntroPage: View {
    let account: AccountViewModel
    let done: () -> Void
    @Environment(\.authorizationController) private var authorizationController
    @State private var signingIn = false
    var body: some View {
        // Same as the sign-in sheet (2026-10-25 사용자): the lockup alone, then Apple → Google → notice and two quiet buttons.
        CenteredMessage(title: nil) {
            DearbyLockup()
        } actions: {
            if let message = account.message, !signingIn {
                DearbyFieldError(text: message).accessibilityIdentifier("intro-message")
            }
            if account.appleSignInEnabled {
                DearbyAppleButton { Task { await account.signInWithApple(using: authorizationController) } }
                    .disabled(account.phase == .signingIn).accessibilityIdentifier("intro-apple")
            }
            if account.googleSignInEnabled {
                DearbyGoogleButton { Task { await account.signInWithGoogle(using: authorizationController) } }
                    .disabled(account.phase == .signingIn).accessibilityIdentifier("intro-google")
            }
            if account.appleSignInEnabled || account.googleSignInEnabled { SignInPrivacyNotice() }
            Button { signingIn = true } label: { Text("패스키로 로그인").frame(maxWidth: .infinity, minHeight: 44) }
                .buttonStyle(.borderless).tint(DearbyStyle.teal).accessibilityIdentifier("intro-passkey")
            Button(action: done) { Text("둘러보기").frame(maxWidth: .infinity, minHeight: 44) }
                .buttonStyle(.borderless).tint(DearbyStyle.teal).accessibilityIdentifier("intro-browse")
                .accessibilityHint("로그인 없이 앱을 둘러봐요.")
        }
        .background(Color(.systemBackground))
        .tint(DearbyStyle.teal)
        .sheet(isPresented: $signingIn) { NavigationStack { SignInSheet(account: account, signedIn: done) } }
        // Apple or Google sign-in from this page; while the sheet is open, the sheet finishes instead.
        .onChange(of: account.phase) { _, phase in if phase == .signedIn && !signingIn { done() } }
        .onChange(of: account.pendingLink) { _, link in if link != nil { signingIn = true } }
    }
}
