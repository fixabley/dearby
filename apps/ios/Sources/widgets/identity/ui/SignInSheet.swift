import AuthenticationServices
import SwiftUI

/// Sign-in shown only when an action needs an account: Apple (when the build enables it), Google (until the server says
/// it is off) and an existing passkey are peers; a new passkey starts a new account. A first-time Apple or Google account
/// links to this device's passkey account or starts a separate one by itself (`AccountViewModel.linkOrStart`); the
/// new-or-link choice below appears only when the person closed that passkey sheet.
struct SignInSheet: View {
    let account: AccountViewModel
    let signedIn: () -> Void
    @Environment(\.authorizationController) private var authorizationController
    @Environment(\.dismiss) private var dismiss
    private enum Action { case apple, google, passkey, newPasskey, pendingNewAccount, pendingLink }
    @State private var running: Action?
    private var busy: Bool { account.phase == .signingIn }
    private var social: Bool { account.appleSignInEnabled || account.googleSignInEnabled }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                DearbyBrand().padding(.vertical, 8)
                if let link = account.pendingLink { pendingChoice(link) } else { choices }
            }.padding(20)
        }
        .navigationTitle("로그인").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        .onChange(of: account.phase) { _, phase in
            if phase == .signedIn { dismiss(); signedIn() }
        }
        .onDisappear { account.clearPendingLink() }
    }

    @ViewBuilder private var choices: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(social ? "로그인" : "패스키로 로그인")
                .font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.ink).accessibilityAddTraits(.isHeader)
            Text(social ? "애플·구글 계정이나 패스키로 바로 로그인해요. 비밀번호와 인증번호는 필요 없어요."
                 : "Face ID나 Touch ID로 바로 로그인해요. 비밀번호와 인증번호는 필요 없어요.")
                .font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
        }
        message
        if account.appleSignInEnabled {
            DearbyAppleButton { run(.apple) }.disabled(busy).accessibilityIdentifier("sign-in-apple")
        }
        if account.googleSignInEnabled {
            DearbyGoogleButton { run(.google) }.disabled(busy).accessibilityIdentifier("sign-in-google")
        }
        Button { run(.passkey) } label: {
            if busy && running == .passkey { ProgressView().tint(.white) } else { Text("패스키로 로그인") }
        }.buttonStyle(DearbyButtonStyle()).disabled(busy).accessibilityIdentifier("sign-in-passkey")
        VStack(alignment: .leading, spacing: 8) {
            Text("처음이거나 이 기기에 패스키가 없나요?").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
            Button { run(.newPasskey) } label: {
                if busy && running == .newPasskey { ProgressView().tint(DearbyStyle.teal) } else { Text("새 패스키로 시작") }
            }.buttonStyle(DearbyButtonStyle(outlined: true)).disabled(busy).accessibilityIdentifier("sign-in-new-passkey")
        }.padding(.top, 8)
    }

    /// After a 202 whose device-passkey sheet was closed: the server cannot tell whether this person already has a passkey account, so they pick.
    @ViewBuilder private func pendingChoice(_ link: AccountViewModel.PendingLink) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("처음 보는 \(link.providerName) 계정이에요").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.ink).accessibilityAddTraits(.isHeader)
            Text("패스키 확인을 건너뛰었어요. 이미 패스키로 만든 Dearby 계정이 있으면 연결하고, 없으면 새 계정으로 시작해요.")
                .font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
            if let email = link.email, !email.isEmpty {
                Text(email).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.ink).accessibilityIdentifier("sign-in-link-email")
            }
        }
        message
        Button { run(.pendingNewAccount) } label: {
            if busy && running == .pendingNewAccount { ProgressView().tint(.white) } else { Text("새 계정으로 시작") }
        }.buttonStyle(DearbyButtonStyle()).disabled(busy).accessibilityIdentifier("sign-in-link-new")
        Button { run(.pendingLink) } label: {
            if busy && running == .pendingLink { ProgressView().tint(DearbyStyle.teal) } else { Text("기존 패스키 계정에 연결") }
        }.buttonStyle(DearbyButtonStyle(outlined: true)).disabled(busy).accessibilityIdentifier("sign-in-link-passkey")
        Button("다른 방법으로 로그인") { account.clearPendingLink() }
            .font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).frame(maxWidth: .infinity)
            .disabled(busy).accessibilityIdentifier("sign-in-link-back")
    }

    @ViewBuilder private var message: some View {
        if let message = account.message {
            DearbyFieldError(text: message).accessibilityIdentifier("sign-in-message")
        }
    }

    private func run(_ action: Action) {
        running = action
        Task {
            switch action {
            case .apple: await account.signInWithApple(using: authorizationController)
            case .google: await account.signInWithGoogle(using: authorizationController)
            case .passkey: await account.signIn(using: authorizationController)
            case .newPasskey: await account.register(using: authorizationController)
            case .pendingNewAccount: await account.registerPendingAccount()
            case .pendingLink: await account.linkPendingToPasskey(using: authorizationController)
            }
        }
    }
}
