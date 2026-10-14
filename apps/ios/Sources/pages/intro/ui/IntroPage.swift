import SwiftUI

/// First-launch intro. Signing in is optional (App Store 5.1.1): "둘러보기" always opens the app.
/// "패스키로 시작하기" reuses the passkey sign-in sheet; closing it after a cancel or failure stays here.
struct IntroPage: View {
    let account: AccountViewModel
    let done: () -> Void
    @State private var signingIn = false
    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            DearbyLogo(width: 160)
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
                Button("패스키로 시작하기") { signingIn = true }
                    .buttonStyle(DearbyButtonStyle()).accessibilityIdentifier("intro-passkey")
                Button("둘러보기", action: done)
                    .buttonStyle(DearbyButtonStyle(outlined: true)).accessibilityIdentifier("intro-browse")
                    .accessibilityHint("로그인 없이 앱을 둘러봐요.")
            }.frame(maxWidth: 420)
        }
        .padding(.horizontal, 24).padding(.bottom, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity).background(.white)
        .tint(DearbyStyle.teal).preferredColorScheme(.light)
        .sheet(isPresented: $signingIn) { NavigationStack { SignInSheet(account: account, signedIn: done) } }
    }
}
