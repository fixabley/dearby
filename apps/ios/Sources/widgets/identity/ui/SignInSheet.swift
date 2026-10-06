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
            }.padding(20)
        }
        .navigationTitle("로그인").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        .onChange(of: account.phase) { _, phase in
            if phase == .signedIn { dismiss(); signedIn() }
        }
    }
    @ViewBuilder private var errorMessage: some View {
        if let message = account.message {
            DearbyFieldError(text: message).accessibilityIdentifier("sign-in-message")
        }
    }
    private var emailForm: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text("이메일로 로그인").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.ink).accessibilityAddTraits(.isHeader)
                Text("인증번호를 메일로 보내 드려요. 비밀번호는 필요 없어요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
            }
            DearbyInlineField(label: "이메일", text: $email, editing: true, prompt: "name@example.com")
                .keyboardType(.emailAddress).textContentType(.emailAddress)
                .textInputAutocapitalization(.never).autocorrectionDisabled().accessibilityIdentifier("sign-in-email")
            errorMessage
            Button { send() } label: {
                if account.phase == .sendingCode { ProgressView().tint(.white) } else { Text("인증번호 받기") }
            }.buttonStyle(DearbyButtonStyle()).disabled(busy || email.isEmpty)
        }
    }
    private var codeForm: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text("메일함을 확인해 주세요").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.ink).accessibilityAddTraits(.isHeader)
                (Text(account.email).bold().foregroundColor(DearbyStyle.ink) + Text("으로 보낸 6자리 인증번호를 입력해 주세요. 5분 동안 쓸 수 있어요."))
                    .font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
                Button("이메일 바꾸기") { code = ""; account.changeEmail() }
                    .font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal).frame(minHeight: 44)
            }
            DearbyCodeField(code: $code, label: "인증번호", isError: account.message != nil, identifier: "sign-in-code")
            errorMessage
            Button { Task { await account.verify(code) } } label: {
                if account.phase == .verifying { ProgressView().tint(.white) } else { Text("로그인") }
            }.buttonStyle(DearbyButtonStyle()).disabled(busy || code.count != 6)
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let wait = max(0, Int(resendAt.timeIntervalSince(context.date).rounded(.up)))
                Button(wait > 0 ? "\(wait)초 후 다시 받을 수 있어요" : "인증번호 다시 받기") { send() }
                    .font(.dearby(.subheadline).weight(.semibold))
                    .foregroundStyle(wait > 0 || busy ? DearbyStyle.quiet : DearbyStyle.teal)
                    .frame(maxWidth: .infinity, minHeight: 44).disabled(busy || wait > 0)
            }
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
