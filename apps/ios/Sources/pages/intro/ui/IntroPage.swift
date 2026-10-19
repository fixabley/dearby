import AuthenticationServices
import SwiftUI

/// First-launch intro. Signing in is optional (App Store 5.1.1): "둘러보기" always opens the app.
/// "패스키로 시작하기" reuses the sign-in sheet; closing it after a cancel or failure stays here.
/// The Apple button (when the build enables it) and the Google button (until the server says it is off) sign in here;
/// a first-time Apple or Google account opens the sheet's new-or-link choice.
struct IntroPage: View {
    let account: AccountViewModel
    let done: () -> Void
    @Environment(\.authorizationController) private var authorizationController
    @State private var signingIn = false
    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            DearbyBrand(symbolSize: 96)
            Text("활동에서 만난 사람과\n명함을 주고받아요")
                .font(.dearby(.title2)).bold().foregroundStyle(DearbyStyle.ink).multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader).padding(.top, 20)
            Text("로그인하지 않아도 둘러볼 수 있어요.")
                .font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
                .padding(.top, 8)
            Spacer()
            VStack(spacing: 12) {
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
                Button("패스키로 시작하기") { signingIn = true }
                    .buttonStyle(DearbyButtonStyle()).accessibilityIdentifier("intro-passkey")
                Button("둘러보기", action: done)
                    .buttonStyle(DearbyButtonStyle(outlined: true)).accessibilityIdentifier("intro-browse")
                    .accessibilityHint("로그인 없이 앱을 둘러봐요.")
            }.frame(maxWidth: 420)
        }
        .padding(.horizontal, 24).padding(.bottom, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity).background(Color(.systemBackground))
        .tint(DearbyStyle.teal)
        .sheet(isPresented: $signingIn) { NavigationStack { SignInSheet(account: account, signedIn: done) } }
        // Apple or Google sign-in from this page; while the sheet is open, the sheet finishes instead.
        .onChange(of: account.phase) { _, phase in if phase == .signedIn && !signingIn { done() } }
        .onChange(of: account.pendingLink) { _, link in if link != nil { signingIn = true } }
    }
}
