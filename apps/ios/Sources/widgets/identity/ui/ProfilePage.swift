import SwiftUI

/// 내 프로필: data only (name, job, introduction, contacts, histories). A profile that was never saved (`version` 0)
/// starts with the step funnel (`ProfileFunnelPage`); afterwards fields are edited in place in an inset-grouped `Form`
/// (contract "폼은 플랫폼 기본 컴포넌트") and once anything differs from the loaded profile the 저장 / 원래대로 bar appears
/// (contract "프로필과 버전 이력"). Card decoration lives in 명함 만들기. Sign-in is asked for only from the 로그인 button.
struct ProfilePage: View {
    let state: ProfileModel
    @State private var form: ProfileForm?
    @State private var funnel: HistoryFunnelTarget?
    @State private var composing = false
    @State private var signingIn = false
    @State private var saving = false
    @State private var error: String?
    @State private var tried = false
    @State private var confirmingDeletion = false
    @State private var deletionNotice: String?
    /// Editor rows (`name`·`introduction`·`phone`·`email`·추가 연락처 id) and the 직무 search; the keyboard 완료 clears both.
    @FocusState private var field: String?
    @FocusState private var jobSearch: Bool
    /// Never saved yet: the step funnel replaces the editor until the first save.
    private var settingUp: Bool { state.profile?.version == 0 }
    var body: some View {
        Group {
            if state.profile != nil, state.consentPending {
                // The consent gate comes before the first profile step and whenever the server says so (contract "개인정보 동의").
                ConsentPage(state: state)
            } else if settingUp, let binding = Binding($form) {
                ProfileFunnelPage(form: binding, account: state.account, saving: saving, error: error) { save(binding.wrappedValue) }
            } else {
                editorScreen
            }
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        // System navigation bar on every branch (contract "iOS 상단 바는 시스템 내비게이션 바"); the funnel sets its own title inside.
        .navigationTitle("내 프로필").navigationBarTitleDisplayMode(.inline)
        // One block so the order is fixed: 변경 이력 · 명함 만들기 (editor only) left of 설정 · 알림.
        .toolbar {
            if case .loaded = state.phase, !state.consentPending, !settingUp {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    NavigationLink { ProfileVersionsPage(state: state) } label: { Image(systemName: "clock") }
                        .accessibilityLabel("변경 이력").accessibilityIdentifier("profile-versions")
                    Button { composing = true } label: { Image(systemName: "person.text.rectangle") }
                        .accessibilityLabel("명함 만들기").accessibilityIdentifier("profile-compose")
                }
            }
            TabRootToolbar()
        }
        .task { await state.load() }
        // A reload (first load, save, restore, evidence) resets the form to the server state unless an edit is in flight.
        .onChange(of: state.profile, initial: true) { _, profile in
            guard let profile else { form = nil; return }
            if form == nil || form?.changed == false || saving { form = ProfileForm(profile: profile); error = nil; tried = false }
        }
        .sheet(item: $funnel) { target in
            NavigationStack {
                HistoryFunnelPage(draft: target.draft, state: state, saved: target.saved, startAtSummary: target.saved) { done in form?.put(done) }
            }
        }
        .sheet(isPresented: $composing, onDismiss: { Task { await state.load() } }) { NavigationStack { CardComposerPage(account: state.account) } }
        .sheet(isPresented: $signingIn) { NavigationStack { SignInSheet(account: state.account) { Task { await state.load() } } } }
        .alert("계정을 삭제할까요?", isPresented: $confirmingDeletion) {
            Button("삭제", role: .destructive) { deleteAccount() }
            Button("취소", role: .cancel) {}
        } message: { Text("명함과 받은 명함 등 모든 정보가 지워지고 되돌릴 수 없어요.") }
        .alert(deletionNotice ?? "", isPresented: Binding(get: { deletionNotice != nil }, set: { if !$0 { deletionNotice = nil } })) {
            Button("확인", role: .cancel) {}
        }
    }
    private var editorScreen: some View {
        Group {
            switch state.phase {
            case .signedOut:
                CenteredMessage(symbol: "person.crop.circle", title: "프로필은 계정에 저장돼요", message: "명함을 만들거나 로그인하면 프로필을 저장하고 고칠 수 있어요.",
                                primary: MessageAction("명함 만들기") { composing = true }, secondary: MessageAction("로그인") { signingIn = true })
            case .loading:
                ProgressView("프로필을 불러오는 중이에요").frame(maxWidth: .infinity, maxHeight: .infinity).tint(DearbyStyle.teal)
            case .failed:
                CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "프로필을 불러오지 못했어요", message: "연결을 확인하고 다시 시도해 주세요.",
                                primary: MessageAction("다시 시도") { Task { await state.load() } },
                                secondary: MessageAction("계정 삭제", role: .destructive, identifier: "profile-delete-account") { confirmingDeletion = true })
            case .loaded(let profile):
                if let binding = Binding($form) { editor(binding, saved: profile) }
            }
        }
        .safeAreaInset(edge: .bottom) { if let form, form.changed { saveBar(form) } }
    }
    /// Rows are stacked label-over-input (`DearbyFormRow`, contract "폼 라벨·입력 크기" 2026-10-20); headers and footers are `.subheadline`.
    private func editor(_ form: Binding<ProfileForm>, saved: AccountProfile) -> some View {
        Form {
            Section {
                DearbyFormRow(label: "이름", focus: $field, value: "name") {
                    TextField("실명 또는 활동명", text: form.name).textContentType(.name).accessibilityLabel("이름, 필수")
                }
            } header: {
                Text("기본 정보").font(.dearby(.subheadline))
            } footer: {
                VStack(alignment: .leading, spacing: 4) {
                    Text("이름은 꼭 적어 주세요. 명함과 공개 프로필에 보여요.")
                    if tried, let message = form.wrappedValue.nameError { DearbyFieldError(text: message) }
                }.font(.dearby(.subheadline))
            }
            Section {
                JobTagPicker(form: form, account: state.account, focus: $jobSearch)
            } header: {
                Text("직무").font(.dearby(.subheadline))
            } footer: {
                if let note = JobTagPicker.note(for: form.wrappedValue) { Text(note).font(.dearby(.subheadline)) }
            }
            Section {
                DearbyFormRow(label: "소개", focus: $field, value: "introduction") {
                    TextField("처음 만난 사람에게 건넬 한두 문장", text: form.introduction, axis: .vertical).lineLimit(3...6).accessibilityLabel("소개")
                }
            }
            ProfileContactFields(phone: form.phone, email: form.email, extras: form.extras, focus: $field,
                                 phoneError: tried ? form.wrappedValue.phoneError : nil, emailError: tried ? form.wrappedValue.emailError : nil) { form.wrappedValue.addContact($0) }
            Section {
                if form.wrappedValue.histories.isEmpty { Text("아직 이력이 없어요. 조직이나 활동부터 골라 단계별로 적어요.").foregroundStyle(.secondary) }
                ForEach(form.wrappedValue.histories) { history in
                    HistoryRowView(history: history, saved: saved.histories.contains { $0.id == history.id }) {
                        funnel = HistoryFunnelTarget(draft: HistoryDraft(history: history), saved: saved.histories.contains { $0.id == history.id })
                    } remove: { form.wrappedValue.removeHistory(history.id) }
                }
                Button { funnel = HistoryFunnelTarget(draft: HistoryDraft(), saved: false) } label: {
                    Label("이력 추가", systemImage: "plus").frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                }
            } header: {
                Text("활동 이력").font(.dearby(.subheadline))
            } footer: {
                if !form.wrappedValue.histories.isEmpty { Text("이력을 누르면 고치거나 인증자료를 올리고, 왼쪽으로 밀어 지워요.").font(.dearby(.subheadline)) }
            }
            Section { deleteButton }
        }
        .font(.dearby(.body)).dearbyDismissKeyboard { field = nil; jobSearch = false }
    }
    private func saveBar(_ form: ProfileForm) -> some View {
        VStack(spacing: 8) {
            if let error { DearbyFieldError(text: error).frame(maxWidth: .infinity, alignment: .leading) }
            HStack(spacing: 10) {
                Button { revert() } label: { Text("원래대로").frame(maxWidth: .infinity) }.buttonStyle(.bordered).disabled(saving)
                Button { save(form) } label: {
                    Group { if saving { ProgressView().tint(DearbyStyle.onTeal) } else { Text("저장") } }.frame(maxWidth: .infinity)
                }.buttonStyle(.borderedProminent).tint(DearbyStyle.tealFill).disabled(saving).accessibilityIdentifier("profile-save")
            }.controlSize(.large).font(.dearby(.body).weight(.semibold))
        }.padding(.horizontal, 20).padding(.vertical, 12).background(.bar)
    }
    /// 원래대로: back to the loaded profile; after a 409 it also fetches the newer one.
    private func revert() {
        error = nil
        tried = false
        if let profile = state.profile { form = ProfileForm(profile: profile) }
        Task { await state.load() }
    }
    private func save(_ form: ProfileForm) {
        tried = true
        guard form.isValid else { error = "빨간 안내를 확인해 주세요."; return }
        saving = true
        Task {
            error = await state.save(form)
            saving = false
            if error == nil, let profile = state.profile { self.form = ProfileForm(profile: profile); tried = false }
        }
    }
    /// App Store 5.1.1(v): shown whenever signed in.
    private var deleteButton: some View {
        Button(role: .destructive) { confirmingDeletion = true } label: { Text("계정 삭제").frame(maxWidth: .infinity, minHeight: 44) }
            .disabled(state.account.deleting).accessibilityIdentifier("profile-delete-account")
    }
    private func deleteAccount() {
        Task {
            let result = await state.account.deleteAccount()
            deletionNotice = result.notice
            guard result != .failed else { return }
            form = nil
            await state.load()
        }
    }
}

/// Which history the funnel sheet edits; `saved` enables evidence uploads.
struct HistoryFunnelTarget: Identifiable {
    let draft: HistoryDraft
    let saved: Bool
    var id: String { draft.id }
}

/// One history row in the profile form: 조직 · 프로그램 · 활동, role, period, and the approval / verification state.
/// Tapping opens the funnel; swiping left deletes.
struct HistoryRowView: View {
    let history: AccountHistory
    let saved: Bool
    let open: () -> Void
    let remove: () -> Void
    var body: some View {
        Button(action: open) {
            VStack(alignment: .leading, spacing: 4) {
                Text(history.chain.joined(separator: " · ")).fontWeight(.semibold).foregroundStyle(DearbyStyle.ink).multilineTextAlignment(.leading)
                Text([history.roleTitle, ContactRules.period(start: history.startDate, end: history.endDate)].filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.dearby(.footnote)).foregroundStyle(.secondary)
                HStack(spacing: 6) {
                    tag(history.isVerified ? "확인됨" : "미확인", strong: history.isVerified)
                    if history.hasCustomEntry || history.approval != "approved" {
                        tag(history.approval == "approved" ? "승인됨" : history.approval == "rejected" ? "거절됨" : "승인 대기", strong: false)
                    }
                    if !saved { tag("저장 전", strong: false) }
                }
            }.frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).padding(.vertical, 2).contentShape(Rectangle())
        }
        .accessibilityHint("눌러서 고치거나 인증자료를 올려요")
        .swipeActions { Button("삭제", role: .destructive, action: remove).accessibilityLabel("이력 삭제") }
    }
    private func tag(_ text: String, strong: Bool) -> some View {
        Text(text).font(.dearby(.caption2).weight(.semibold)).foregroundStyle(strong ? DearbyStyle.teal : DearbyStyle.quiet)
            .padding(.horizontal, 7).padding(.vertical, 2).background(strong ? DearbyStyle.mint : DearbyStyle.muted, in: Capsule())
    }
}
