import AuthenticationServices
import SwiftUI

/// 사용자가 골라 추가한 연락처 한 줄의 값. `verifiedValue`는 서버에서 인증된 값(계약 "연락처 인증" 2026-10-26; 인증되지 않았으면 nil).
struct ProfileExtraContact: Identifiable, Equatable {
    let id: String
    let kind: String
    var value: String
    var verifiedValue: String? = nil
    init(id: String, kind: String, value: String, verifiedValue: String? = nil) {
        self.id = id; self.kind = kind; self.value = value; self.verifiedValue = verifiedValue
    }
    /// A saved contact as a row: a profile URL saved before 2026-10-26 reads as its username (contract "연락처 사용자명 입력").
    init(_ contact: AccountContact) {
        let value = ContactModel.username(kind: contact.kind, contact.value) ?? contact.value
        self.init(id: contact.id, kind: contact.kind, value: value, verifiedValue: contact.verified ? value : nil)
    }
    /// The row still carries its check: the typed value means the same as the verified one (email apart from case and spaces, a
    /// username after `ContactModel.username`), as the server compares before it keeps or drops `verified` on save.
    var verified: Bool { Self.stillVerified(kind: kind, typed: value, verified: verifiedValue) }
    static func stillVerified(kind: String, typed: String, verified: String?) -> Bool {
        guard let verified else { return false }
        func normal(_ text: String) -> String {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return kind == "email" ? trimmed.lowercased() : ContactModel.username(kind: kind, trimmed) ?? trimmed
        }
        return normal(typed) == normal(verified)
    }
}

/// 프로필 연락처 섹션(폼 `Section`). 전화번호·이메일은 필수 행이고, 그 밖의 종류는 `연락처 추가` 행에서 골라 붙이고 왼쪽으로 밀어 지운다.
/// 행은 왼쪽에 종류 아이콘(`ContactModel.glyph(kind:)`, 브랜드 네 종류는 단색 마크)을 둔 플로팅 라벨 `DearbyFormRow`이고 종류 이름이 라벨(비면 placeholder 자리)이다(계약 "연락처 행은 라벨 대신 왼쪽 아이콘"·"플로팅 라벨" 2026-10-20);
/// 행 어디를 눌러도 그 필드에 포커스가 간다(`focus`의 값은 `phone`·`email`·추가 연락처의 id·인증번호 칸 `emailCode`).
/// 연락처 인증(계약 "연락처 인증" 2026-10-26): 이메일 행 아래 `인증번호 받기`(`EmailVerifyRow`), GitHub 행 아래 `GitHub으로 연결`(`GitHubLinkRow`). 인증된 행은 값 끝에 체크, 값이
/// 바뀌면 "값을 바꾸면 인증이 풀려요". 인증이 끝나면 서버가 새 버전을 저장하므로 `form.absorbVerification`으로 버전과 그 행만 받는다. 503이면 그 버튼은 숨긴다.
/// 값·형식 검사·오류 문구는 `form`이 갖고(`tried`일 때 footer) 이 위젯은 행으로 보인다.
struct ProfileContactFields: View {
    @Binding var form: ProfileForm
    let state: ProfileModel
    var focus: FocusState<String?>.Binding
    /// 저장을 눌렀다: 전화번호·이메일 오류를 footer에 보인다.
    var tried = false
    @State private var choosing = false
    /// A kind was picked: once the sheet is gone its new row takes the focus (and the keyboard).
    @State private var added = false
    static let kinds: [(kind: String, label: String)] = [("kakao", "카카오톡"), ("instagram", "Instagram"), ("github", "GitHub"), ("behance", "Behance")]
    var body: some View {
        Section {
            DearbyFormRow(label: "전화번호", symbol: ContactModel.glyph(kind: "phone"), required: true, text: $form.phone, focus: focus, value: "phone") {
                $0.keyboardType(.phonePad).textContentType(.telephoneNumber)
            }
            VStack(alignment: .leading, spacing: 8) {
                DearbyFormRow(label: "이메일", symbol: ContactModel.glyph(kind: "email"), required: true, text: $form.email, focus: focus, value: "email", verified: form.emailVerified) {
                    $0.keyboardType(.emailAddress).textContentType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                }
                if !form.emailVerified {
                    EmailVerifyRow(email: form.email, canSend: form.emailError == nil, note: note(verified: form.emailVerifiedValue, still: false, value: "email"), state: state,
                                   focus: focus) { form.absorbVerification($0, kind: "email") }
                } else if let note = note(verified: form.emailVerifiedValue, still: true, value: "email") {
                    Text(note).font(.dearby(.footnote)).foregroundStyle(.secondary)
                }
            }
            ForEach($form.extras) { $item in
                let label = Self.kinds.first { $0.kind == item.kind }?.label ?? item.kind
                // Username kinds take the username only; a pasted URL is reduced to it (contract "연락처 사용자명 입력" 2026-10-26).
                let value = Binding { $item.wrappedValue.value } set: { $item.wrappedValue.value = ContactModel.sanitizedUsername(kind: item.kind, $0) }
                VStack(alignment: .leading, spacing: 8) {
                    DearbyFormRow(label: label, symbol: ContactModel.glyph(kind: item.kind), prefix: ContactModel.usernamePrefix(kind: item.kind), text: value, focus: focus,
                                  value: item.id, verified: item.verified) {
                        $0.textInputAutocapitalization(.never).autocorrectionDisabled()
                    }
                    if item.kind == "github", !item.verified {
                        GitHubLinkRow(note: note(verified: item.verifiedValue, still: false, value: item.id), state: state) { form.absorbVerification($0, kind: "github") }
                    } else if let note = note(verified: item.verifiedValue, still: item.verified, value: item.id) {
                        Text(note).font(.dearby(.footnote)).foregroundStyle(.secondary)
                    }
                }
                .swipeActions {
                    Button("삭제", role: .destructive) { form.extras.removeAll { $0.id == item.id } }.accessibilityLabel("\(label) 삭제")
                }
            }
            Button { choosing = true } label: {
                Label("연락처 추가", systemImage: "plus").frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
            }
            .sheet(isPresented: $choosing, onDismiss: {
                if added { added = false; focus.wrappedValue = form.extras.last?.id }
            }) {
                NavigationStack {
                    List(Self.kinds, id: \.kind) { option in
                        Button { added = true; choosing = false; form.addContact(option.kind) } label: {
                            Label { Text(option.label).foregroundStyle(DearbyStyle.ink) } icon: {
                                DearbyGlyphView(glyph: ContactModel.glyph(kind: option.kind))
                            }.font(.dearby(.body)).frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                        }
                    }
                    .navigationTitle("추가할 연락처").navigationBarTitleDisplayMode(.inline)
                    .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { choosing = false } } }
                }.presentationDetents([.medium])
            }
        } header: {
            Text("연락처").font(.dearby(.subheadline))
        } footer: {
            VStack(alignment: .leading, spacing: 4) {
                Text("추가한 연락처는 왼쪽으로 밀어 지워요.")
                if tried, let phoneError = form.phoneError { DearbyFieldError(text: phoneError) }
                if tried, let emailError = form.emailError { DearbyFieldError(text: emailError) }
            }.font(.dearby(.subheadline))
        }
    }
    /// Under a row whose saved value was verified: while it is focused (about to be edited) or once it differs (the check is gone until
    /// 저장 drops or, back at the verified value, keeps it).
    private func note(verified: String?, still: Bool, value: String) -> String? {
        verified != nil && (!still || focus.wrappedValue == value) ? "값을 바꾸면 인증이 풀려요" : nil
    }
}

/// 이메일 인증번호 (contract "연락처 인증" 2026-10-26): `인증번호 받기` → `POST /v1/me/contacts/email/challenges` → the same row shows the 6-digit field
/// (number pad, `oneTimeCode`), `확인` → `…/verify`, and `다시 받기` after a minute (countdown). The server's 400·429 wording shows as is; a wrong
/// code stays in the row with the tries left, a gone challenge (`CHALLENGE_EXPIRED`) goes back to 인증번호 받기. Changing the address drops the
/// challenge. 503 hides the button and says the feature is off for now. 사용 통계 `contact_verify(kind=email, result)`.
private struct EmailVerifyRow: View {
    let email: String
    let canSend: Bool
    /// "값을 바꾸면 인증이 풀려요" when the saved email was verified.
    let note: String?
    let state: ProfileModel
    var focus: FocusState<String?>.Binding
    let verified: (AccountProfile) -> Void
    @State private var challenge: Challenge?
    @State private var code = ""
    @State private var busy = false
    @State private var message: String?
    static let resendAfter: TimeInterval = 60
    struct Challenge: Equatable { let id: String; let email: String; let sentAt: Date }
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let challenge {
                DearbyFormRow(label: "인증번호", text: $code, focus: focus, value: "emailCode") { $0.keyboardType(.numberPad).textContentType(.oneTimeCode) }
                HStack(spacing: 12) {
                    // Ticks once a second until the minute is up, then stays as it is.
                    TimelineView(.explicit(stride(from: 1.0, through: Self.resendAfter, by: 1).map { challenge.sentAt.addingTimeInterval($0) })) { context in
                        let left = Int((Self.resendAfter - context.date.timeIntervalSince(challenge.sentAt)).rounded(.up))
                        Button(left > 0 ? "다시 받기 (\(left)초)" : "다시 받기") { Task { await send() } }.disabled(left > 0 || busy).monospacedDigit()
                    }
                    Spacer(minLength: 0)
                    Button { Task { await verify(challenge) } } label: {
                        if busy { ProgressView() } else { Text("확인").fontWeight(.semibold) }
                    }.disabled(code.count != 6 || busy).accessibilityIdentifier("contact-email-verify")
                }.buttonStyle(.borderless).font(.dearby(.subheadline))
                Text("메일로 보낸 6자리 인증번호를 10분 안에 입력해 주세요.").font(.dearby(.footnote)).foregroundStyle(.secondary)
            } else {
                HStack(spacing: 12) {
                    if let note { Text(note).font(.dearby(.footnote)).foregroundStyle(.secondary) }
                    Spacer(minLength: 0)
                    if state.emailVerifyAvailable {
                        Button { Task { await send() } } label: {
                            if busy { ProgressView() } else { Text("인증번호 받기").fontWeight(.semibold) }
                        }.buttonStyle(.borderless).font(.dearby(.subheadline)).disabled(!canSend || busy).accessibilityIdentifier("contact-email-send")
                    }
                }
            }
            if let message { DearbyFieldError(text: message) }
        }
        // The code was for the address as typed then; a new address starts over.
        .onChange(of: email) { _, _ in if challenge != nil { challenge = nil; code = ""; message = nil } }
    }
    private func send() async {
        busy = true
        message = nil
        defer { busy = false }
        do {
            let answer = try await state.requestEmailCode(email.trimmingCharacters(in: .whitespacesAndNewlines))
            challenge = Challenge(id: answer.challengeId, email: email, sentAt: .now)
            code = ""
            focus.wrappedValue = "emailCode"
        } catch AccountError.rateLimited(let detail) {
            message = detail ?? "인증번호는 1분에 한 번 받을 수 있어요."
        } catch AccountError.disabled {
            message = "이메일 인증을 지금은 쓸 수 없어요. 주소는 그대로 저장돼요."
        } catch {
            message = state.message(error, fallback: "인증번호를 보내지 못했어요. 잠시 후 다시 시도해 주세요.")
        }
    }
    private func verify(_ challenge: Challenge) async {
        busy = true
        message = nil
        defer { busy = false }
        do {
            message = try await state.verifyEmail(challengeId: challenge.id, code: code)
            if message == nil, let profile = state.profile {
                AppAnalytics.event("contact_verify", ["kind": "email", "result": "ok"])
                self.challenge = nil
                code = ""
                verified(profile)
                return
            }
        } catch AccountError.challengeExpired(let detail) {
            message = detail
            self.challenge = nil
            code = ""
        } catch { message = state.message(error, fallback: "인증하지 못했어요. 잠시 후 다시 시도해 주세요.") }
        AppAnalytics.event("contact_verify", ["kind": "email", "result": "failed"])
    }
}

/// GitHub 연결 (contract "연락처 인증" 2026-10-26): `start` → the authorize page in `ASWebAuthenticationSession` (SwiftUI `webAuthenticationSession`,
/// callback scheme `dearby`) → `dearby://contacts/github?code=…` → `complete` → the profile with the GitHub row verified (`verified`). Cancelling
/// in the sheet or on GitHub (`?error=cancelled`) is silent; `failed` and a 400 show one line. 503 hides the button. 사용 통계
/// `contact_verify(kind=github, result)`. The app does not route this URL itself (`IncomingLink` ignores `contacts`).
private struct GitHubLinkRow: View {
    let note: String?
    let state: ProfileModel
    let verified: (AccountProfile) -> Void
    @Environment(\.webAuthenticationSession) private var webAuth
    @State private var busy = false
    @State private var message: String?
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                if let note { Text(note).font(.dearby(.footnote)).foregroundStyle(.secondary) }
                Spacer(minLength: 0)
                if state.githubLinkAvailable {
                    Button { Task { await link() } } label: {
                        if busy { ProgressView() } else { Text("GitHub으로 연결").fontWeight(.semibold) }
                    }.buttonStyle(.borderless).font(.dearby(.subheadline)).disabled(busy).accessibilityIdentifier("contact-github-link")
                }
            }
            if let message { DearbyFieldError(text: message) }
        }
    }
    private func link() async {
        busy = true
        message = nil
        defer { busy = false }
        let result: String
        do {
            let url = try await state.githubAuthorizeURL()
            let callback = try await webAuth.authenticate(using: url, callback: .customScheme("dearby"), preferredBrowserSession: nil, additionalHeaderFields: [:])
            let query = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems ?? []
            if let code = query.first(where: { $0.name == "code" })?.value {
                message = await state.completeGitHub(code: code)
                if message == nil, let profile = state.profile { verified(profile) }
                result = message == nil ? "ok" : "failed"
            } else if query.first(where: { $0.name == "error" })?.value == "cancelled" {
                result = "cancelled"
            } else {
                message = "GitHub 연결에 실패했어요. 다시 시도해 주세요."
                result = "failed"
            }
        } catch let error as ASWebAuthenticationSessionError where error.code == .canceledLogin {
            result = "cancelled"
        } catch AccountError.disabled {
            message = "GitHub 연결을 지금은 쓸 수 없어요. 사용자명은 그대로 저장돼요."
            result = "failed"
        } catch {
            message = state.message(error, fallback: "GitHub 연결에 실패했어요. 다시 시도해 주세요.")
            result = "failed"
        }
        AppAnalytics.event("contact_verify", ["kind": "github", "result": result])
    }
}

/// 활동 이력의 기간 입력 행들(폼 섹션 안): 고른 기간 `2026.03 – 2026.06`(`DearbyValueRow`), 시작일(필수)·종료일은 `DearbyFieldBox` 상자 안의 시스템 `DatePicker`(compact)와
/// `진행 중` 스위치(라벨 바로 옆, 행 전체 터치). 날짜 검사(종료일이 시작일보다 앞서지 않음)와 오류 문구는 화면이 footer에 둔다.
struct HistoryPeriodFields: View {
    @Binding var start: Date?
    @Binding var end: Date?
    @Binding var ongoing: Bool
    var body: some View {
        DearbyValueRow(label: "기간", value: Self.period(start: start, end: ongoing ? nil : end, ongoing: ongoing))
        dateRow("시작일", required: true, date: $start, earliest: nil)
        Toggle("진행 중", isOn: $ongoing)
        if !ongoing { dateRow("종료일", required: false, date: $end, earliest: start) }
    }
    /// `earliest`가 있으면(종료일) 그보다 앞선 날을 고를 수 없다. 날짜가 없으면 행 전체가 `날짜 선택` 버튼이고 오늘(또는 `earliest`)로 채운다.
    private func dateRow(_ label: String, required: Bool, date: Binding<Date?>, earliest: Date?) -> some View {
        DearbyFieldBox(label: label) {
            if let value = date.wrappedValue {
                DatePicker(label, selection: Binding(get: { value }, set: { date.wrappedValue = $0 }),
                           in: (earliest ?? .distantPast)..., displayedComponents: .date)
                    .labelsHidden().environment(\.locale, Locale(identifier: "ko_KR"))
                    .accessibilityLabel(required ? "\(label), 필수" : label)
            } else {
                Button { date.wrappedValue = max(.now, earliest ?? .distantPast) } label: {
                    Text("날짜 선택").font(.dearby(.body)).foregroundStyle(DearbyStyle.teal)
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                }
                .buttonStyle(.borderless).accessibilityLabel("\(label) 선택\(required ? ", 필수" : "")")
            }
        }
    }
    /// 연·월 위주 표시: `2026.03 – 2026.06`, 진행 중이면 `2026.03 – 진행 중`.
    static func period(start: Date?, end: Date?, ongoing: Bool) -> String {
        guard let start else { return "시작일을 골라 주세요" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "yyyy.MM"
        let tail = ongoing ? "진행 중" : end.map { formatter.string(from: $0) } ?? "종료일 미정"
        return "\(formatter.string(from: start)) – \(tail)"
    }
}

#if DEBUG
/// 미리보기 예시. 연락처는 실제가 아닌 example.invalid 값이고 서버에는 닿지 않는다.
struct ProfileFieldsSample: View {
    @State var form = ProfileForm(profile: AccountProfile(
        name: "지민", contacts: [AccountContact(id: "p", kind: "phone", label: "", value: "010-12"),
                               AccountContact(id: "e", kind: "email", label: "", value: "jimin@example.invalid", verified: true),
                               AccountContact(id: "x1", kind: "instagram", label: "", value: "jimin"), AccountContact(id: "x2", kind: "github", label: "", value: "")],
        histories: [], version: 3))
    @State var state = ProfileModel(account: AccountViewModel(client: .live(api: URL(string: "https://localhost")!), vault: TokenVault()))
    @State var start: Date? = ISO8601DateFormatter().date(from: "2026-03-01T00:00:00Z")
    @State var end: Date? = ISO8601DateFormatter().date(from: "2026-06-01T00:00:00Z")
    @State var ongoing = false
    @FocusState var focus: String?
    var body: some View {
        Form {
            ProfileContactFields(form: $form, state: state, focus: $focus, tried: true)
            Section("기간") { HistoryPeriodFields(start: $start, end: $end, ongoing: $ongoing) }
        }.font(.dearby(.body)).dearbyDismissKeyboard { focus = nil }
    }
}

#Preview("프로필 입력") { ProfileFieldsSample() }
#endif
