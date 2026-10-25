import AuthenticationServices
import SwiftUI

/// Sign-in shown only when an action needs an account: Apple (when the build enables it), Google (until the server says
/// it is off) and an existing passkey are peers; new accounts start only with Apple or Google (2026-10-25). A first-time Apple or Google account
/// links to this device's passkey account or starts a separate one by itself (`AccountViewModel.linkOrStart`); the
/// new-or-link choice below appears only when the person closed that passkey sheet.
/// Layout is the centred message (contract "비스크롤 화면 배치·CTA"): the lockup image (wordmark + catchphrase), title, explanation in the middle; the buttons stacked at the bottom.
struct SignInSheet: View {
    let account: AccountViewModel
    let signedIn: () -> Void
    @Environment(\.authorizationController) private var authorizationController
    @Environment(\.dismiss) private var dismiss
    private enum Action { case apple, google, passkey, pendingNewAccount, pendingLink }
    @State private var running: Action?
    private var busy: Bool { account.phase == .signingIn }
    private var social: Bool { account.appleSignInEnabled || account.googleSignInEnabled }
    var body: some View {
        Group {
            if let link = account.pendingLink { pendingChoice(link) } else { choices }
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("로그인").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        .onChange(of: account.phase) { _, phase in
            if phase == .signedIn { dismiss(); signedIn() }
        }
        .onDisappear { account.clearPendingLink() }
        // Fixed half height (2026-10-25): on a small screen the heading scrolls and the buttons stay in view.
        .presentationDetents([.medium])
    }

    private var choices: some View {
        CenteredMessage(title: social ? "로그인" : "패스키로 로그인",
                        message: social ? "애플·구글 계정이나 패스키로 바로 로그인해요. 비밀번호와 인증번호는 필요 없어요."
                            : "Face ID나 Touch ID로 바로 로그인해요. 비밀번호와 인증번호는 필요 없어요.") {
            DearbyLockup()
        } actions: {
            message
            if account.appleSignInEnabled {
                DearbyAppleButton { run(.apple) }.disabled(busy).accessibilityIdentifier("sign-in-apple")
            }
            if account.googleSignInEnabled {
                DearbyGoogleButton { run(.google) }.disabled(busy).accessibilityIdentifier("sign-in-google")
            }
            button("패스키로 로그인", .passkey).accessibilityIdentifier("sign-in-passkey")
        }
    }

    /// After a 202 whose device-passkey sheet was closed: the server cannot tell whether this person already has a passkey account, so they pick.
    private func pendingChoice(_ link: AccountViewModel.PendingLink) -> some View {
        CenteredMessage(symbol: "person.crop.circle", title: "처음 보는 \(link.providerName) 계정이에요",
                        message: "패스키 확인을 건너뛰었어요. 이미 패스키로 만든 Dearby 계정이 있으면 연결하고, 없으면 새 계정으로 시작해요.") {
            if let email = link.email, !email.isEmpty {
                Text(email).font(.dearby(.footnote)).foregroundStyle(.secondary).accessibilityIdentifier("sign-in-link-email")
            }
            message
            button("새 계정으로 시작", .pendingNewAccount).accessibilityIdentifier("sign-in-link-new")
            button("기존 패스키 계정에 연결", .pendingLink).accessibilityIdentifier("sign-in-link-passkey")
            Button { account.clearPendingLink() } label: { Text("다른 방법으로 로그인").frame(maxWidth: .infinity, minHeight: 44) }
                .buttonStyle(.borderless).tint(DearbyStyle.teal).disabled(busy).accessibilityIdentifier("sign-in-link-back")
        }
    }

    @ViewBuilder private var message: some View {
        if let message = account.message {
            DearbyFieldError(text: message).accessibilityIdentifier("sign-in-message")
        }
    }

    /// A full-width `.borderedProminent` system button showing a spinner while its action runs.
    private func button(_ title: String, _ action: Action) -> some View {
        Button { run(action) } label: {
            Group {
                if busy && running == action { ProgressView().tint(DearbyStyle.onTeal) } else { Text(title) }
            }.frame(maxWidth: .infinity)
        }.buttonStyle(.borderedProminent).disabled(busy)
    }

    private func run(_ action: Action) {
        running = action
        Task {
            switch action {
            case .apple: await account.signInWithApple(using: authorizationController)
            case .google: await account.signInWithGoogle(using: authorizationController)
            case .passkey: await account.signIn(using: authorizationController)
            case .pendingNewAccount: await account.registerPendingAccount()
            case .pendingLink: await account.linkPendingToPasskey(using: authorizationController)
            }
        }
    }
}
