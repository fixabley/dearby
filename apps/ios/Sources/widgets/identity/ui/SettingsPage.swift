import AuthenticationServices
import SwiftUI

/// 설정 (contract "설정" 2026-10-19 밤), pushed from any tab's title line: an inset-grouped `List` of 계정 (가입일, 로그인 수단, 로그아웃, 계정 삭제),
/// 연결된 로그인 (Apple·Google: linked → email·date·연결 해제, else 연결하기), 패스키 (rename·delete by swipe or tap, 패스키 추가) and 정보
/// (policy pages in the in-app browser, version). Signed out it shows only the 로그인 CTA and 정보. The last sign-in method cannot be
/// removed: its button is disabled with the reason, and a server 409 says the same.
struct SettingsPage: View {
    @State private var model: SettingsModel
    let webOrigin: URL
    @Environment(\.authorizationController) private var authorizationController
    @State private var signingIn = false
    @State private var confirmingSignOut = false
    @State private var confirmingDeletion = false
    /// The passkey whose row was tapped (이름 바꾸기 / 삭제 choice) and the one being renamed (text alert).
    @State private var choosing: PasskeySummary?
    @State private var renaming: PasskeySummary?
    @State private var newLabel = ""
    @State private var opening: URL?
    init(account: AccountViewModel, webOrigin: URL) {
        _model = State(initialValue: SettingsModel(account: account))
        self.webOrigin = webOrigin
    }
    private var account: AccountViewModel { model.account }
    var body: some View {
        List {
            switch model.phase {
            case .signedOut: signIn
            case .loading: Section { ProgressView().frame(maxWidth: .infinity, minHeight: 88) }
            case .failed:
                Section {
                    Label("설정을 불러오지 못했어요", systemImage: "exclamationmark.triangle").foregroundStyle(.secondary)
                    Button("다시 시도") { Task { await model.load() } }
                }
            case .loaded:
                accountSection
                identitiesSection
                passkeysSection
                Section {
                    NavigationLink { BlockedPeoplePage(account: account) } label: { Label("차단한 사람", systemImage: "hand.raised") }
                } footer: { Text("차단한 사람의 명함은 받은 명함에 들어오지 않아요.").font(.dearby(.footnote)) }
            }
            about
        }
        .listStyle(.insetGrouped).font(.dearby(.body)).disabled(model.busy)
        .navigationTitle("설정").navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar).toolbar(.hidden, for: .tabBar)
        .task { await model.load() }
        .sheet(isPresented: $signingIn) { NavigationStack { SignInSheet(account: account) { Task { await model.load() } } } }
        .sheet(item: $opening) { url in SafariView(url: url).ignoresSafeArea() }
        .confirmationDialog("로그아웃할까요?", isPresented: $confirmingSignOut, titleVisibility: .visible) {
            Button("로그아웃", role: .destructive) { Task { await model.signOut() } }
            Button("취소", role: .cancel) {}
        } message: { Text("이 기기에서만 로그아웃돼요. 패스키와 연결된 계정은 그대로 남아요.") }
        .alert("계정을 삭제할까요?", isPresented: $confirmingDeletion) {
            Button("삭제", role: .destructive) { deleteAccount() }
            Button("취소", role: .cancel) {}
        } message: { Text("명함과 받은 명함 등 모든 정보가 지워지고 되돌릴 수 없어요.") }
        .alert("알려 드려요", isPresented: Binding(get: { model.notice != nil }, set: { if !$0 { model.notice = nil } })) {
            Button("확인", role: .cancel) {}
        } message: { Text(model.notice ?? "") }
        .confirmationDialog(choosing?.label ?? "패스키", isPresented: Binding(get: { choosing != nil }, set: { if !$0 { choosing = nil } }),
                            titleVisibility: .visible, presenting: choosing) { passkey in
            Button("이름 바꾸기") { startRenaming(passkey) }
            if !model.isLastSignInMethod { Button("삭제", role: .destructive) { Task { await model.delete(passkey) } } }
            Button("취소", role: .cancel) {}
        } message: { _ in
            if model.isLastSignInMethod { Text("마지막 로그인 수단이라 지울 수 없어요.") }
        }
        .alert("패스키 이름", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } }), presenting: renaming) { passkey in
            TextField("예: 내 아이폰", text: $newLabel)
            Button("저장") { Task { await model.rename(passkey, to: newLabel) } }
            Button("취소", role: .cancel) {}
        } message: { _ in Text("1자 이상 60자 이하") }
    }

    /// Signed out: one 로그인 CTA on the list background (contract "로그인하지 않았으면 … 로그인 CTA와 개인정보·약관·버전만").
    private var signIn: some View {
        Section {
            Button { signingIn = true } label: { Text("로그인").frame(maxWidth: .infinity) }
                .buttonStyle(.borderedProminent).controlSize(.large).tint(DearbyStyle.tealFill).font(.dearby(.body).weight(.semibold))
                .accessibilityIdentifier("settings-sign-in")
                .listRowBackground(Color.clear).listRowInsets(EdgeInsets(top: 8, leading: 4, bottom: 8, trailing: 4))
        } footer: {
            Text("로그인하면 가입일, 연결된 로그인, 패스키를 관리할 수 있어요.").font(.dearby(.footnote))
        }
    }
    private var accountSection: some View {
        Section {
            if let summary = model.summary {
                LabeledContent("가입일", value: Self.day(summary.createdAt))
                LabeledContent("로그인 수단", value: methods(summary.signInMethods))
            }
            Button("로그아웃") { confirmingSignOut = true }.accessibilityIdentifier("settings-sign-out")
            Button("계정 삭제", role: .destructive) { confirmingDeletion = true }
                .disabled(account.deleting).accessibilityIdentifier("settings-delete-account")
        } header: { Text("계정").font(.dearby(.footnote)) }
    }
    private var identitiesSection: some View {
        Section {
            identityRow("Apple", provider: "apple", kind: .apple, available: account.appleSignInEnabled)
            identityRow("Google", provider: "google", kind: .google, available: account.googleSignInEnabled)
        } header: {
            Text("연결된 로그인").font(.dearby(.footnote))
        } footer: {
            if model.isLastSignInMethod, !model.identities.isEmpty {
                Text("마지막 로그인 수단은 해제할 수 없어요. 패스키나 다른 로그인을 먼저 추가해 주세요.").font(.dearby(.footnote))
            }
        }
    }
    /// Linked: the provider, masked email and date, with 연결 해제 (off when it is the last way in). Not linked: 연결하기 when the build offers that sign-in.
    @ViewBuilder private func identityRow(_ name: String, provider: String, kind: AccountViewModel.LinkProvider, available: Bool) -> some View {
        if let linked = model.identity(provider) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(name)
                    Text([linked.email, "연결 " + Self.day(linked.linkedAt)].compactMap { $0 }.joined(separator: " · "))
                        .font(.dearby(.footnote)).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                Button("연결 해제") { Task { await model.unlink(provider) } }
                    .buttonStyle(.borderless).disabled(model.isLastSignInMethod).accessibilityIdentifier("settings-unlink-\(provider)")
            }
        } else if available {
            HStack(spacing: 12) {
                Text(name)
                Spacer(minLength: 0)
                Button("연결하기") { Task { await model.link(kind, using: authorizationController) } }
                    .buttonStyle(.borderless).accessibilityIdentifier("settings-link-\(provider)")
            }
        }
    }
    private var passkeysSection: some View {
        Section {
            if model.passkeys.isEmpty { Text("등록된 패스키가 없어요.").foregroundStyle(.secondary) }
            ForEach(model.passkeys) { passkey in
                Button { choosing = passkey } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(passkey.label.isEmpty ? "패스키" : passkey.label).foregroundStyle(.primary)
                        Text(detail(passkey)).font(.dearby(.footnote)).foregroundStyle(.secondary)
                    }.frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                }
                .accessibilityHint("눌러서 이름을 바꾸거나 지워요")
                .swipeActions {
                    if !model.isLastSignInMethod {
                        Button("삭제", role: .destructive) { Task { await model.delete(passkey) } }
                    }
                    Button("이름 바꾸기") { startRenaming(passkey) }.tint(DearbyStyle.teal)
                }
            }
            Button { Task { await model.addPasskey(using: authorizationController) } } label: { Label("패스키 추가", systemImage: "plus") }
                .accessibilityIdentifier("settings-add-passkey")
        } header: {
            Text("패스키").font(.dearby(.footnote))
        } footer: {
            Text(model.isLastSignInMethod && !model.passkeys.isEmpty
                 ? "마지막 로그인 수단은 지울 수 없어요. 패스키를 하나 더 만들거나 다른 로그인을 연결해 주세요."
                 : "이 계정으로 로그인할 수 있는 패스키예요. 밀거나 눌러서 이름을 바꾸거나 지워요.").font(.dearby(.footnote))
        }
    }
    private var about: some View {
        Section {
            Button { opening = webOrigin.appending(path: "privacy") } label: { Label("개인정보 처리방침", systemImage: "arrow.up.right") }
            Button { opening = webOrigin.appending(path: "terms") } label: { Label("이용약관", systemImage: "arrow.up.right") }
            LabeledContent("버전", value: Self.version)
        } header: { Text("정보").font(.dearby(.footnote)) }
    }

    private func startRenaming(_ passkey: PasskeySummary) {
        newLabel = passkey.label
        renaming = passkey
    }
    private func deleteAccount() {
        Task {
            let result = await account.deleteAccount()
            if result != .failed { await model.load() }
            model.notice = result.notice
        }
    }
    private func methods(_ methods: AccountSummary.SignInMethods) -> String {
        var parts: [String] = []
        if methods.passkeys > 0 { parts.append("패스키 \(methods.passkeys)개") }
        if methods.apple { parts.append("Apple") }
        if methods.google { parts.append("Google") }
        return parts.isEmpty ? "없음" : parts.joined(separator: " · ")
    }
    private func detail(_ passkey: PasskeySummary) -> String {
        ["만든 날 " + Self.day(passkey.createdAt),
         passkey.lastUsedAt.map { "마지막 사용 " + Self.day($0) } ?? "아직 사용 안 함",
         passkey.backedUp ? "기기 간 동기화됨" : "이 기기에만"].joined(separator: " · ")
    }
    /// `yyyy. M. d.` in Korean from a server timestamp; the raw text if it does not parse.
    static func day(_ iso: String) -> String {
        guard let date = ProfileVersionsPage.parse(iso) else { return iso }
        return date.formatted(.dateTime.year().month().day().locale(Locale(identifier: "ko_KR")))
    }
    /// `MARKETING_VERSION (CURRENT_PROJECT_VERSION)` from the generated Info.plist.
    static var version: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "—"
        let build = info?["CFBundleVersion"] as? String ?? "—"
        return "\(short) (\(build))"
    }
}
