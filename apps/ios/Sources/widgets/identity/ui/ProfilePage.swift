import SwiftUI

/// 내 프로필: the account's profile with required phone and email (#149). Card making stays one tap away even
/// signed out; sign-in is asked for only from the 로그인 button or when saving.
struct ProfilePage: View {
    let state: ProfileModel
    @State private var form: ProfileForm?
    @State private var composing = false
    @State private var signingIn = false
    @Environment(\.openURL) private var openURL
    var body: some View {
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
                case .loaded(let profile):
                    HStack {
                        Text("내 프로필").font(.dearby(.title2).bold()).accessibilityAddTraits(.isHeader)
                        Spacer()
                        Button("편집") { form = ProfileForm(profile: profile, signInEmail: state.account.email) }
                    }
                    IdentityHeading(name: profile.name.isEmpty ? "이름 없음" : profile.name, job: profile.job,
                                    introduction: profile.introduction, isFullProfile: true)
                    Divider()
                    Text("연락처").font(.dearby(.headline))
                    if profile.contacts.isEmpty { Text("저장한 연락처가 없어요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet) }
                    ContactIcons(contacts: profile.contacts.map { ContactModel(id: $0.id, kind: $0.kind, displayLabel: $0.label, value: $0.value) }) { _ in }
                    Divider()
                    Text("활동 이력").font(.dearby(.headline))
                    if profile.histories.isEmpty { Text("저장한 활동 이력이 없어요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet) }
                    HistoryTimeline(histories: profile.histories.map {
                        HistoryModel(id: $0.id, title: $0.title, role: $0.role, startDate: String($0.startDate.prefix(7)).replacingOccurrences(of: "-", with: "."),
                                     endDate: $0.endDate.map { String($0.prefix(7)).replacingOccurrences(of: "-", with: ".") } ?? "진행 중")
                    }, compact: true)
                }
            }.padding(20)
        }
        .background(.white).toolbar(.hidden, for: .navigationBar)
        .task { await state.load() }
        .sheet(item: $form) { draft in NavigationStack { ProfileEditPage(state: state, form: draft) } }
        .sheet(isPresented: $composing, onDismiss: { Task { await state.load() } }) {
            NavigationStack { CardComposerPage(account: state.account) }
        }
        .sheet(isPresented: $signingIn) { NavigationStack { SignInSheet(account: state.account) { Task { await state.load() } } } }
    }
}

extension ProfileForm: Identifiable { var id: String { "profile-form" } }

/// Edits the profile; saving keeps the form open with the error when it fails.
struct ProfileEditPage: View {
    let state: ProfileModel
    @State var form: ProfileForm
    @State private var saving = false
    @State private var error: String?
    @State private var tried = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                section("기본 정보") {
                    field("이름", text: $form.name, prompt: "실명 또는 활동명", required: true)
                    if tried, let message = form.nameError { DearbyFieldError(text: message) }
                    field("직무", text: $form.job, prompt: "예: 서비스 기획 · 커뮤니티")
                    field("소개", text: $form.introduction, prompt: "처음 만난 사람에게 건넬 한두 문장", multiline: true)
                }
                section("연락처") {
                    ProfileContactFields(phone: $form.phone, email: $form.email, extras: $form.extras,
                                         phoneError: tried ? form.phoneError : nil, emailError: tried ? form.emailError : nil) { form.addContact($0) }
                }
                section("활동 이력") {
                    ForEach($form.histories) { $history in
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                field("활동 이름", text: $history.title, prompt: "예: Dearby 메이커 캠프", required: true)
                                Button { form.histories.removeAll { $0.id == history.id } } label: {
                                    Image(systemName: "minus.circle").foregroundStyle(DearbyStyle.quiet).frame(width: 44, height: 44)
                                }.accessibilityLabel("활동 이력 삭제")
                            }
                            field("역할", text: $history.role, prompt: "예: 서비스 기획")
                            HistoryPeriodFields(start: $history.start, end: $history.end, ongoing: $history.ongoing,
                                                error: tried ? form.historyError(history) : nil)
                        }.padding(14).overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.line))
                    }
                    Button { form.addHistory() } label: {
                        Label("활동 이력 추가", systemImage: "plus").frame(maxWidth: .infinity, minHeight: 44)
                    }.buttonStyle(DearbyButtonStyle(outlined: true))
                }
            }.padding(20)
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 8) {
                if let error { Text(error).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.ink) }
                Button { save() } label: {
                    if saving { ProgressView().tint(.white) } else { Text("저장") }
                }.buttonStyle(DearbyButtonStyle()).disabled(saving)
            }.padding(.horizontal, 20).padding(.vertical, 12).background(.white)
        }
        .background(.white).navigationTitle("프로필 편집").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
    }
    private func save() {
        tried = true
        guard form.isValid else { error = "빨간 안내를 확인해 주세요."; return }
        saving = true
        Task {
            error = await state.save(form)
            saving = false
            if error == nil { dismiss() }
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
