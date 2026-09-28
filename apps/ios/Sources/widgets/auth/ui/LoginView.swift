import SwiftUI

struct LoginView: View {
    let state: LoginState
    let login: (String, String) async throws -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var email = ""
    @State private var code = ""
    @State private var challengeID: String?
    @State private var expiresAt = ""
    @State private var message: String?
    @State private var busy = false
    var body: some View {
        NavigationStack {
            Form {
                Text("이메일로 받은 인증번호를 입력해 주세요.")
                TextField("이메일", text: $email).keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never).autocorrectionDisabled().disabled(challengeID != nil)
                Button(challengeID == nil ? "인증번호 받기" : "인증번호 다시 받기") {
                    busy = true
                    Task {
                        defer { busy = false }
                        do {
                            let result = try await state.challenge(email: email)
                            challengeID = result.challengeId; expiresAt = result.expiresAt
                            message = "인증번호를 요청했습니다. 이메일을 확인해 주세요."
                        } catch { message = error.localizedDescription }
                    }
                }.disabled(busy || email.isEmpty)
                if let challengeID {
                    TextField("인증번호", text: $code).keyboardType(.numberPad).textContentType(.oneTimeCode)
                    Text("만료: \(expiresAt)").font(.caption)
                    Button("로그인") {
                        busy = true
                        Task {
                            defer { busy = false }
                            do { try await login(challengeID, code); dismiss() } catch { message = error.localizedDescription }
                        }
                    }.disabled(busy || code.isEmpty)
                }
                if let message { Text(message).font(.footnote) }
            }.navigationTitle("이메일 로그인")
                .toolbar { Button("닫기") { dismiss() }.disabled(busy) }
                .interactiveDismissDisabled(busy)
        }
    }
}
