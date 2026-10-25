import SwiftUI

/// 내 프로필: data only (name, job, introduction, contacts, histories). A profile that was never saved (`version` 0)
/// starts with the step funnel (`ProfileFunnelPage`). Afterwards the tab is **read mode** (`ProfileReadView`, contract "프로필 읽기/수정 모드" 2026-10-20);
/// the toolbar `수정` (pencil) swaps in the inset-grouped `Form` editor with the 저장 / 원래대로 bar once anything differs from the loaded profile
/// (contract "프로필과 버전 이력"). `수정` becomes `완료`: no change → back to reading, changes → 저장 / 버리기 / 취소. A successful save returns to read mode.
/// 변경 이력·명함 만들기 live in the toolbar `더 보기` menu. Card decoration lives in 명함 만들기. Signed out (contract "비로그인에서 만들기·추천을 막음"
/// 2026-10-25) there is nothing to make or edit: the page offers 로그인, and `지원 자격 정보`·`관심 분야·활동 종류` open sign-in too (the app opens it for the latter).
/// The consent gate (contract "개인정보 동의", "동의 시트 닫기" 2026-10-20) is a sheet over the funnel/editor whenever the server says
/// `consentRequired`: pulling it down or 닫기 means not consented — the sheet stays away until a save comes back 403 `CONSENT_REQUIRED`, which shows it again.
struct ProfilePage: View {
    let state: ProfileModel
    /// 프로필 `추천` 묶음 (contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24): `지원 자격 정보` and `관심 분야·활동 종류`.
    let eligibility: EligibilityModel
    /// Reopens the 추천 funnel (signed out, the app opens sign-in first).
    let editRecommendations: () -> Void
    @State private var eligibilityConsent = false
    @State private var eligibilityAgreed = false
    @State private var eligibilityInput = false
    @State private var eligibilityChoice = false
    @State private var eligibilityError: String?
    @State private var form: ProfileForm?
    @State private var funnel: HistoryFunnelTarget?
    /// Read mode is the default; `editing` lives only for the app run.
    @State private var editing = false
    @State private var confirmingDone = false
    @State private var showVersions = false
    @State private var composing = false
    @State private var signingIn = false
    @State private var saving = false
    @State private var error: String?
    @State private var tried = false
    /// 닫기/pull-down on the consent sheet; cleared when a save is refused for missing consent so the sheet comes back.
    @State private var consentDismissed = false
    /// Editor rows (`name`·`introduction`·`phone`·`email`·추가 연락처 id) and the 직무 search; the keyboard 완료 clears both.
    @FocusState private var field: String?
    @FocusState private var jobSearch: Bool
    /// Never saved yet: the step funnel replaces the editor until the first save.
    private var settingUp: Bool { state.profile?.version == 0 }
    var body: some View {
        Group {
            if settingUp, let binding = Binding($form) {
                ProfileFunnelPage(form: binding, account: state.account, saving: saving, error: error) { save(binding.wrappedValue) }
            } else {
                editorScreen
            }
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        // System navigation bar on every branch (contract "iOS 상단 바는 시스템 내비게이션 바"); the funnel sets its own title inside.
        .navigationTitle("내 프로필").navigationBarTitleDisplayMode(.inline)
        // [수정/완료] [설정] [알림] [더 보기: 변경 이력 · 명함 만들기] (contract "상단 바 구성"); the profile-only parts appear once a saved profile is loaded.
        .toolbar {
            TabRootToolbar(menu: toolbarReady ? menuItems : []) {
                if toolbarReady {
                    Button(action: done) {
                        if editing { Text("완료") } else { Image(systemName: "pencil") }
                    }.accessibilityLabel(editing ? "완료" : "수정").accessibilityIdentifier("profile-edit")
                }
            }
        }
        .confirmationDialog("바뀐 내용을 저장할까요?", isPresented: $confirmingDone, titleVisibility: .visible) {
            Button("저장") { if let form { save(form) } }
            Button("버리기", role: .destructive) { revert(); editing = false }
            Button("취소", role: .cancel) {}
        }
        .navigationDestination(isPresented: $showVersions) { ProfileVersionsPage(state: state) }
        // Leaving `.loaded` (sign-out from 설정, a reload failure) also leaves edit mode.
        .onChange(of: toolbarReady) { _, ready in if !ready { editing = false } }
        .task { await state.load() }
        // A reload (first load, save, restore, evidence) resets the form to the server state unless an edit is in flight.
        .onChange(of: state.profile, initial: true) { _, profile in
            guard let profile else { form = nil; return }
            if form == nil || form?.changed == false || saving { form = ProfileForm(profile: profile); error = nil; tried = false }
        }
        .sheet(isPresented: Binding(get: { state.profile != nil && state.consentPending && !consentDismissed }, set: { if !$0 { consentDismissed = true } })) {
            NavigationStack { ConsentPage(state: state) }
        }
        // A fresh requirement (sign-in to another account, a new policy version) shows the sheet again even if an earlier one was closed.
        .onChange(of: state.consentPending) { _, pending in if pending { consentDismissed = false } }
        .sheet(item: $funnel) { target in
            NavigationStack {
                HistoryFunnelPage(draft: target.draft, state: state, saved: target.saved, startAtSummary: target.saved) { done in form?.put(done) }
            }
        }
        .sheet(isPresented: $composing, onDismiss: { Task { await state.load() } }) { NavigationStack { CardComposerPage(account: state.account) } }
        .sheet(isPresented: $signingIn) { NavigationStack { SignInSheet(account: state.account) { Task { await state.load() } } } }
        .sheet(isPresented: $eligibilityConsent, onDismiss: { if eligibilityAgreed { eligibilityAgreed = false; eligibilityInput = true } }) {
            EligibilityConsentSheet(webOrigin: state.webOrigin) { eligibilityAgreed = true; eligibilityConsent = false }
                .presentationDetents([.medium, .large]).presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $eligibilityInput) { EligibilityFlow(model: eligibility) }
        .confirmationDialog("지원 자격 정보", isPresented: $eligibilityChoice, titleVisibility: .hidden) {
            Button("고치기") { eligibilityInput = true }
            Button("지원 자격 정보 지우기", role: .destructive) { Task { eligibilityError = await eligibility.delete() } }
            Button("취소", role: .cancel) {}
        }
        .alert(eligibilityError ?? "", isPresented: Binding(get: { eligibilityError != nil }, set: { if !$0 { eligibilityError = nil } })) {
            Button("확인", role: .cancel) {}
        }
    }
    /// `추천` 묶음; `지원 자격 정보` is hidden on servers without its API.
    private var recommendationGroup: some View {
        let showsEligibility = eligibility.phase != .unavailable
        return VStack(alignment: .leading, spacing: 6) {
            Text("추천").font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(.secondary).padding(.horizontal, 16)
                .accessibilityAddTraits(.isHeader)
            VStack(spacing: 0) {
                if showsEligibility {
                    groupRow("지원 자격 정보", detail: eligibility.summary ?? "입력하면 지원할 수 있는 활동만 추천해요", action: openEligibility)
                        .accessibilityIdentifier("profile-eligibility")
                    Divider().padding(.leading, 16)
                }
                groupRow("관심 분야·활동 종류", action: editRecommendations).accessibilityIdentifier("profile-recommendation")
            }
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 14, style: .continuous))
        }
    }
    private func groupRow(_ title: String, detail: String? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.dearby(.body)).foregroundStyle(.primary)
                    if let detail { Text(detail).font(.dearby(.footnote)).foregroundStyle(.secondary) }
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 16).padding(.vertical, 8).frame(minHeight: 44).contentShape(Rectangle())
        }.buttonStyle(.plain)
    }
    /// Signed out → sign in. Entered (and consented to this version) → 고치기·지우기. Otherwise the consent sheet, then the questions.
    private func openEligibility() {
        switch eligibility.phase {
        case .signedOut: signingIn = true
        case .loaded where eligibility.hasValues && !eligibility.needsConsent: eligibilityChoice = true
        case .loaded: if eligibility.needsConsent { eligibilityConsent = true } else { eligibilityInput = true }
        case .failed, .loading, .unavailable: Task { await eligibility.load() }
        }
    }
    private var toolbarReady: Bool {
        if case .loaded = state.phase { return !state.consentPending && !settingUp }
        return false
    }
    private var menuItems: [DearbyMenuItem] {
        [DearbyMenuItem("변경 이력", systemImage: "clock") { showVersions = true },
         DearbyMenuItem("명함 만들기", systemImage: "person.text.rectangle") { composing = true }]
    }
    /// 완료: nothing changed → read mode; otherwise ask 저장 / 버리기 / 취소.
    private func done() {
        guard editing else { editing = true; return }
        if form?.changed == true { confirmingDone = true } else { editing = false }
    }
    private var editorScreen: some View {
        Group {
            switch state.phase {
            case .signedOut:
                VStack(spacing: 0) {
                    CenteredMessage(symbol: "person.crop.circle", title: "프로필은 계정에 저장돼요", message: "로그인하면 프로필을 적고 명함을 만들 수 있어요.",
                                    primary: MessageAction("로그인") { signingIn = true })
                    recommendationGroup.padding(.horizontal, 20).padding(.bottom, 16)
                }
            case .loading:
                // Contract "내용 로딩은 스켈레톤" 2026-10-22: the read view with sample values.
                ScrollView { ProfileReadView(profile: .placeholder).padding(20).dearbySkeleton() }.scrollDisabled(true)
            case .failed:
                CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "프로필을 불러오지 못했어요", message: "연결을 확인하고 다시 시도해 주세요.",
                                primary: MessageAction("다시 시도") { Task { await state.load() } })
            case .loaded(let profile):
                if !editing {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 0) {
                            ProfileReadView(profile: profile).padding(20)
                            recommendationGroup.padding(.horizontal, 20).padding(.bottom, 20)
                        }
                    }
                } else if let binding = Binding($form) {
                    editor(binding, saved: profile)
                }
            }
        }
        .safeAreaInset(edge: .bottom) { if editing, let form, form.changed { saveBar(form) } }
        // 저장 막대는 키보드가 올라와도 제자리(화면 아래)에 둔다(사용자 2026-10-20 "원래대로 저장 버튼은 키보드가 올라올 때 제자리에").
        .ignoresSafeArea(.keyboard, edges: .bottom)
    }
    /// Rows are floating-label boxes (`DearbyFormRow`, contract "플로팅 라벨" 2026-10-20); headers and footers are `.subheadline`.
    private func editor(_ form: Binding<ProfileForm>, saved: AccountProfile) -> some View {
        Form {
            Section {
                DearbyFormRow(label: "이름", required: true, text: form.name, focus: $field, value: "name") { $0.textContentType(.name) }
            } header: {
                Text("기본 정보").font(.dearby(.subheadline))
            } footer: {
                VStack(alignment: .leading, spacing: 4) {
                    Text("명함과 공개 프로필에 보여요.")
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
                DearbyFormRow(label: "소개", text: form.introduction, multiline: true, focus: $field, value: "introduction")
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
            // 403 CONSENT_REQUIRED re-raises the consent sheet even after the user closed it (contract "다시 저장하면 다시 뜬다").
            if state.consentPending { consentDismissed = false }
            if error == nil, let profile = state.profile { self.form = ProfileForm(profile: profile); tried = false; editing = false }
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
