import AuthenticationServices
import SwiftUI

/// Passkey sign-in shown only when an action needs an account. Signing in with an existing passkey comes first;
/// a new passkey starts a new account.
struct SignInSheet: View {
    let account: AccountViewModel
    let signedIn: () -> Void
    @Environment(\.authorizationController) private var authorizationController
    @Environment(\.dismiss) private var dismiss
    @State private var creating = false
    private var busy: Bool { account.phase == .signingIn }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("패스키로 로그인").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.ink).accessibilityAddTraits(.isHeader)
                    Text("Face ID나 Touch ID로 바로 로그인해요. 비밀번호와 인증번호는 필요 없어요.")
                        .font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
                }
                if let message = account.message {
                    DearbyFieldError(text: message).accessibilityIdentifier("sign-in-message")
                }
                Button { run(creating: false) } label: {
                    if busy && !creating { ProgressView().tint(.white) } else { Text("패스키로 로그인") }
                }.buttonStyle(DearbyButtonStyle()).disabled(busy).accessibilityIdentifier("sign-in-passkey")
                VStack(alignment: .leading, spacing: 8) {
                    Text("처음이거나 이 기기에 패스키가 없나요?").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
                    Button { run(creating: true) } label: {
                        if busy && creating { ProgressView().tint(DearbyStyle.teal) } else { Text("새 패스키로 시작") }
                    }.buttonStyle(DearbyButtonStyle(outlined: true)).disabled(busy).accessibilityIdentifier("sign-in-new-passkey")
                }.padding(.top, 8)
            }.padding(20)
        }
        .navigationTitle("로그인").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        .onChange(of: account.phase) { _, phase in
            if phase == .signedIn { dismiss(); signedIn() }
        }
    }
    private func run(creating: Bool) {
        self.creating = creating
        Task {
            if creating { await account.register(using: authorizationController) } else { await account.signIn(using: authorizationController) }
        }
    }
}
