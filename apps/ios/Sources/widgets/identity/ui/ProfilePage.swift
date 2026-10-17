import SwiftUI

/// 내 프로필: data only (name, job, introduction, contacts, histories). A profile that was never saved (`version` 0)
/// starts with the step funnel (`ProfileFunnelPage`); afterwards fields are edited in place and once anything differs
/// from the loaded profile the 저장 / 원래대로 bar appears (contract "프로필과 버전 이력"). Card decoration lives in
/// 명함 만들기. Sign-in is asked for only from the 로그인 button.
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
    /// Never saved yet: the step funnel replaces the editor until the first save.
    private var settingUp: Bool { state.profile?.version == 0 }
    var body: some View {
        Group {
            if settingUp, let binding = Binding($form) {
                ProfileFunnelPage(form: binding, saving: saving, error: error) { save(binding.wrappedValue) }
            } else {
                editorScroll
            }
        }
        .background(.white).toolbar(.hidden, for: .navigationBar)
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
    private var editorScroll: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                switch state.phase {
                case .signedOut:
                    VStack(spacing: 12) {
                        Text("내 프로필").font(.dearby(.title2).bold()).accessibilityAddTraits(.isHeader)
                        Text("명함을 만들거나 로그인하면 프로필을 저장하고 고칠 수 있어요.")
                            .font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
                        Button("명함 만들기") { composing = true }.buttonStyle(DearbyButtonStyle())
                        Button("로그인") { signingIn = true }.buttonStyle(DearbyButtonStyle(outlined: true))
                    }.frame(maxWidth: .infinity).padding(.top, 40)
                case .loading:
                    ProgressView("프로필을 불러오는 중이에요").frame(maxWidth: .infinity, minHeight: 240).tint(DearbyStyle.teal)
                case .failed:
                    Text("프로필을 불러오지 못했어요").font(.dearby(.headline))
                    Button("다시 시도") { Task { await state.load() } }.buttonStyle(DearbyButtonStyle(outlined: true))
                    deleteButton
                case .loaded(let profile):
                    if let binding = Binding($form) { editor(binding, saved: profile) }
                }
            }.padding(20)
        }
        .safeAreaInset(edge: .bottom) { if let form, form.changed { saveBar(form) } }
    }
    @ViewBuilder private func editor(_ form: Binding<ProfileForm>, saved: AccountProfile) -> some View {
        HStack {
            Text("내 프로필").font(.dearby(.title2).bold()).accessibilityAddTraits(.isHeader)
            Spacer()
            NavigationLink { ProfileVersionsPage(state: state) } label: {
                Label("변경 이력", systemImage: "clock.arrow.circlepath").font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
            }
            Button("명함 만들기") { composing = true }.font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
        }
        section("기본 정보") {
            field("이름", text: form.name, prompt: "실명 또는 활동명", required: true)
            if tried, let message = form.wrappedValue.nameError { DearbyFieldError(text: message) }
            field("직함", text: form.job, prompt: "예: 서비스 기획 · 커뮤니티")
            field("소개", text: form.introduction, prompt: "처음 만난 사람에게 건넬 한두 문장", multiline: true)
        }
        section("연락처") {
            ProfileContactFields(phone: form.phone, email: form.email, extras: form.extras,
                                 phoneError: tried ? form.wrappedValue.phoneError : nil, emailError: tried ? form.wrappedValue.emailError : nil) { form.wrappedValue.addContact($0) }
        }
        section("활동 이력") {
            if form.wrappedValue.histories.isEmpty { Text("아직 이력이 없어요. 조직이나 활동부터 골라 단계별로 적어요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet) }
            ForEach(form.wrappedValue.histories) { history in
                HistoryRowView(history: history, saved: saved.histories.contains { $0.id == history.id }) {
                    funnel = HistoryFunnelTarget(draft: HistoryDraft(history: history), saved: saved.histories.contains { $0.id == history.id })
                } remove: { form.wrappedValue.removeHistory(history.id) }
            }
            Button { funnel = HistoryFunnelTarget(draft: HistoryDraft(), saved: false) } label: {
                Label("이력 추가", systemImage: "plus").frame(maxWidth: .infinity, minHeight: 44)
            }.buttonStyle(DearbyButtonStyle(outlined: true))
        }
        Divider()
        deleteButton
    }
    private func saveBar(_ form: ProfileForm) -> some View {
        VStack(spacing: 8) {
            if let error { Text(error).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.danger).frame(maxWidth: .infinity, alignment: .leading) }
            HStack(spacing: 10) {
                Button("원래대로") { revert() }.buttonStyle(DearbyButtonStyle(outlined: true)).disabled(saving)
                Button { save(form) } label: { if saving { ProgressView().tint(.white) } else { Text("저장") } }
                    .buttonStyle(DearbyButtonStyle()).disabled(saving).accessibilityIdentifier("profile-save")
            }
        }.padding(.horizontal, 20).padding(.vertical, 12).background(.white).overlay(alignment: .top) { Rectangle().fill(DearbyStyle.line).frame(height: 1) }
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
        Button("계정 삭제", role: .destructive) { confirmingDeletion = true }
            .font(.dearby(.subheadline)).frame(maxWidth: .infinity, minHeight: 44)
            .disabled(state.account.deleting).accessibilityIdentifier("profile-delete-account")
    }
    private func deleteAccount() {
        Task {
            switch await state.account.deleteAccount() {
            case .deleted: deletionNotice = "계정을 삭제했어요. 기기에 남은 패스키는 설정 > 암호에서 지울 수 있어요."
            case .signedOut: deletionNotice = "로그인이 만료됐어요. 다시 로그인해 주세요."
            case .failed: deletionNotice = "계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요."; return
            }
            form = nil
            await state.load()
        }
    }
    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.dearby(.headline)).accessibilityAddTraits(.isHeader)
            content()
        }
    }
    private func field(_ label: String, text: Binding<String>, prompt: String, required: Bool = false, multiline: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label + (required ? " · 필수" : "")).font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.quiet).accessibilityHidden(true)
            DearbyInlineField(label: required ? "\(label), 필수" : label, text: text, editing: true, prompt: prompt, multiline: multiline)
        }
    }
}

/// Which history the funnel sheet edits; `saved` enables evidence uploads.
struct HistoryFunnelTarget: Identifiable {
    let draft: HistoryDraft
    let saved: Bool
    var id: String { draft.id }
}

/// One history in the profile list: 조직 · 프로그램 · 활동, role, period, and the approval / verification state.
struct HistoryRowView: View {
    let history: AccountHistory
    let saved: Bool
    let open: () -> Void
    let remove: () -> Void
    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Button(action: open) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(history.chain.joined(separator: " · ")).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.ink).multilineTextAlignment(.leading)
                    Text([history.roleTitle, ContactRules.period(start: history.startDate, end: history.endDate)].filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                    HStack(spacing: 6) {
                        tag(history.isVerified ? "확인됨" : "미확인", strong: history.isVerified)
                        if history.hasCustomEntry || history.approval != "approved" {
                            tag(history.approval == "approved" ? "승인됨" : history.approval == "rejected" ? "거절됨" : "승인 대기", strong: false)
                        }
                        if !saved { tag("저장 전", strong: false) }
                    }
                }.frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityHint("눌러서 고치거나 인증자료를 올려요")
            Button(action: remove) {
                Image(systemName: "minus.circle").foregroundStyle(DearbyStyle.quiet).frame(width: 44, height: 44)
            }.accessibilityLabel("이력 삭제")
        }.padding(14).overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.line))
    }
    private func tag(_ text: String, strong: Bool) -> some View {
        Text(text).font(.dearby(.caption2).weight(.semibold)).foregroundStyle(strong ? DearbyStyle.teal : DearbyStyle.quiet)
            .padding(.horizontal, 7).padding(.vertical, 2).background(strong ? DearbyStyle.mint : DearbyStyle.muted, in: Capsule())
    }
}
