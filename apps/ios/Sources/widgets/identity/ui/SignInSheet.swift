import SwiftUI

/// Two-step email sign-in shown only when an action needs an account. Messages never say whether
/// the address already has an account.
struct SignInSheet: View {
    let account: AccountViewModel
    let signedIn: () -> Void
    @State private var email = ""
    @State private var code = ""
    @State private var resendAt = Date.distantPast
    @Environment(\.dismiss) private var dismiss
    // Server limit: one code per address per minute.
    private static let resendDelay: TimeInterval = 60
    private var busy: Bool { account.phase == .sendingCode || account.phase == .verifying }
    private var codeStep: Bool { account.phase == .codeSent || account.phase == .verifying }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if codeStep { codeForm } else { emailForm }
                if let message = account.message {
                    Text(message).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.ink)
                        .accessibilityIdentifier("sign-in-message")
                }
            }.padding(20)
        }
        .navigationTitle("로그인").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        .onChange(of: account.phase) { _, phase in
            if phase == .signedIn { dismiss(); signedIn() }
        }
    }
    private var emailForm: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("이메일로 받은 인증번호로 로그인해요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
            DearbyInlineField(label: "이메일", text: $email, editing: true, prompt: "name@example.com")
                .keyboardType(.emailAddress).textContentType(.emailAddress)
                .textInputAutocapitalization(.never).autocorrectionDisabled().accessibilityIdentifier("sign-in-email")
            Button { send() } label: {
                if account.phase == .sendingCode { ProgressView().tint(.white) } else { Text("인증번호 받기") }
            }.buttonStyle(DearbyButtonStyle()).disabled(busy || email.isEmpty)
        }
    }
    private var codeForm: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("\(account.email)으로 보낸 인증번호 6자리를 입력해 주세요. 5분 동안 쓸 수 있어요.")
                .font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
            DearbyInlineField(label: "인증번호", text: $code, editing: true, prompt: "6자리 숫자")
                .keyboardType(.numberPad).textContentType(.oneTimeCode).accessibilityIdentifier("sign-in-code")
            Button { Task { await account.verify(code) } } label: {
                if account.phase == .verifying { ProgressView().tint(.white) } else { Text("로그인") }
            }.buttonStyle(DearbyButtonStyle()).disabled(busy || code.count != 6)
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let wait = Int(resendAt.timeIntervalSince(context.date).rounded(.up))
                Button(wait > 0 ? "\(wait)초 후 다시 받을 수 있어요" : "인증번호 다시 받기") { send() }
                    .buttonStyle(DearbyButtonStyle(outlined: true)).disabled(busy || wait > 0)
            }
            Button("이메일 바꾸기") { code = ""; account.changeEmail() }
                .font(.dearby(.subheadline)).frame(maxWidth: .infinity, minHeight: 44)
        }
    }
    private func send() {
        Task {
            await account.requestCode(email)
            if account.phase == .codeSent && account.message == nil {
                code = ""
                resendAt = .now.addingTimeInterval(Self.resendDelay)
            }
        }
    }
}
