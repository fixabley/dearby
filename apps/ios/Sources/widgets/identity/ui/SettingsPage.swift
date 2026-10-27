import AuthenticationServices
import SwiftUI

/// 설정 (contract "설정" 2026-10-19 밤), pushed from any tab's title line: an inset-grouped `List` of 계정 (가입일, 로그인 수단, 로그아웃, 계정 삭제),
/// 연결된 로그인 (Apple·Google: linked → email·date·연결 해제, else 연결하기), 패스키 (rename·delete by swipe or tap, 패스키 추가) and 정보
/// (policy pages in the in-app browser, version). Above 정보: 알림 (새 모집 알림 and, when the system permission was refused, a row to the
/// app's notification settings; only signed in with consent, since push is an account feature) and 개인정보 (`사용 통계 보내기`, always, and
/// `광고 목적 정보 제공` signed in with consent in a build with ads). Signed out it shows only the 로그인 CTA, 개인정보 and 정보. The last sign-in method cannot be
/// removed: its button is disabled with the reason, and a server 409 says the same.
struct SettingsPage: View {
    @State private var model: SettingsModel
    let webOrigin: URL
    /// 새 모집 알림; nil signed out or before consent (contract "설정" 2026-10-25).
    let notifications: NotificationModel?
    /// 세션 이동 알림 (contract "찜한 세션 이동 알림" 2026-10-26); nil signed out or before consent, as the section it sits in.
    let sessionReminders: SessionReminderModel?
    /// 라이브 액티비티 미리보기 (Debug builds, signed in or not). The `행사 진행 중 표시` switch is gone (contract "행사 안내" 2026-10-27): the Live
    /// Activity starts only from `행사 안내 시작`.
    let liveSession: LiveSessionModel?
    /// 광고 목적 정보 제공; nil signed out or before consent (contract "광고 목적 개인정보 제3자 제공 동의(선택)" 2026-10-25).
    let adConsent: AdConsentModel?
    @Environment(\.authorizationController) private var authorizationController
    @Environment(\.openURL) private var openURL
    /// `사용 통계 보내기`: on until turned off, kept on the device, signed in or not.
    @AppStorage(AppAnalytics.enabledKey) private var analyticsEnabled = true
    @State private var signingIn = false
    @State private var confirmingSignOut = false
    @State private var confirmingDeletion = false
    /// The passkey whose row was tapped (이름 바꾸기 / 삭제 choice) and the one being renamed (text alert).
    @State private var choosing: PasskeySummary?
    @State private var renaming: PasskeySummary?
    @State private var newLabel = ""
    @State private var opening: URL?
    init(account: AccountViewModel, webOrigin: URL, notifications: NotificationModel? = nil, sessionReminders: SessionReminderModel? = nil,
         liveSession: LiveSessionModel? = nil, adConsent: AdConsentModel? = nil) {
        _model = State(initialValue: SettingsModel(account: account))
        self.webOrigin = webOrigin
        self.notifications = notifications
        self.sessionReminders = sessionReminders
        self.liveSession = liveSession
        self.adConsent = QueueAdLoader.enabled ? adConsent : nil
    }
    private var account: AccountViewModel { model.account }
    var body: some View {
        List {
            switch model.phase {
            case .signedOut: signIn
            case .loading:
                // Contract "내용 로딩은 스켈레톤" 2026-10-22: the 계정 section's rows inside one row, so they read as one element.
                Section {
                    VStack(spacing: 0) {
                        LabeledContent("가입일", value: "2026년 1월 1일").frame(minHeight: 44)
                        Divider()
                        LabeledContent("로그인 수단", value: "패스키").frame(minHeight: 44)
                        Divider()
                        Text("로그아웃").frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    }.dearbySkeleton()
                } header: { Text("계정").font(.dearby(.footnote)) }
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
            if let notifications { notificationSection(notifications) }
            #if DEBUG
            if let liveSession { debugSection(liveSession) }
            #endif
            privacySection
            about
        }
        .listStyle(.insetGrouped).font(.dearby(.body)).disabled(model.busy)
        .navigationTitle("설정").navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar).toolbar(.hidden, for: .tabBar)
        .task { await model.load() }
        .task(id: adConsent != nil) { await adConsent?.refresh() }
        .adConsentSheet(adConsent, at: .settings)
        .alert(adConsent?.notice ?? "", isPresented: Binding(get: { adConsent?.notice != nil }, set: { if !$0 { adConsent?.notice = nil } })) {
            Button("확인", role: .cancel) {}
        }
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
    private func notificationSection(_ notifications: NotificationModel) -> some View {
        Section {
            Toggle(isOn: Binding(get: { notifications.newRecruitment ?? true }, set: { on in Task { await notifications.setNewRecruitment(on) } })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("새 모집 알림")
                    Text("즐겨찾기한 프로그램·활동의 새 모집과 일정").font(.dearby(.footnote)).foregroundStyle(.secondary)
                }
            }.tint(DearbyStyle.teal).accessibilityIdentifier("settings-new-recruitment")
            if let sessionReminders {
                // Local (contract "찜한 세션 이동 알림"): off removes every pending reminder at once (`DearbyApp` reschedules on the change); on
                // also asks for the permission while it was never answered, as 새 모집 알림 does.
                Toggle(isOn: Binding(get: { sessionReminders.enabled }, set: { on in
                    sessionReminders.enabled = on
                    if on { Task { await notifications.requestPermissionIfNeeded(); sessionReminders.refresh() } }
                })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("세션 이동 알림")
                        Text("내 시간표의 세션 10분 전에 이 기기에서 알려요").font(.dearby(.footnote)).foregroundStyle(.secondary)
                    }
                }.tint(DearbyStyle.teal).accessibilityIdentifier("settings-session-reminders")
            }
            if notifications.denied {
                Button {
                    AppAnalytics.event("menu_open", ["menu": "notifications"])
                    if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("기기 알림이 꺼져 있어요").foregroundStyle(.primary)
                        Text("설정에서 Dearby 알림을 켜면 받을 수 있어요").font(.dearby(.footnote)).foregroundStyle(.secondary)
                    }
                }.accessibilityIdentifier("settings-notification-settings")
            }
        } header: { Text("알림").font(.dearby(.footnote)) }
        .alert(notifications.notice ?? "", isPresented: Binding(get: { notifications.notice != nil }, set: { if !$0 { notifications.notice = nil } })) {
            Button("확인", role: .cancel) {}
        }
    }
    #if DEBUG
    /// 라이브 액티비티 미리보기 (2026-10-27 사용자 결정), Debug builds only: a made-up 행사 through `LiveSessionModel.togglePreview`, so the 잠금 화면·Dynamic
    /// Island looks can be checked without an event day or an account; each tap moves to the next mode, then ends it.
    private func debugSection(_ liveSession: LiveSessionModel) -> some View {
        Section {
            Button { liveSession.togglePreview() } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text("라이브 액티비티 미리보기").foregroundStyle(.primary)
                    Text(Self.previewHint(liveSession.previewing))
                        .font(.dearby(.footnote)).foregroundStyle(.secondary)
                }
            }.accessibilityIdentifier("settings-live-preview")
        } header: {
            Text("Debug").font(.dearby(.footnote))
        } footer: {
            Text("Debug 빌드에만 있어요. iOS 설정의 실시간 현황이 켜져 있어야 보여요.").font(.dearby(.footnote))
        }
    }
    private static func previewHint(_ mode: LiveSessionModel.PreviewMode?) -> String {
        switch mode {
        case nil: "가짜 행사를 바로 띄워요. 누를 때마다 세션 중 → 이동 → 끝"
        case .running: "세션 중 · 누르면 이동으로"
        case .moving: "이동 · 누르면 끝나요"
        }
    }
    #endif
    private var privacySection: some View {
        Section {
            Toggle(isOn: $analyticsEnabled) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("사용 통계 보내기")
                    Text("어떤 화면과 기능을 쓰는지 모아 앱을 고치는 데 써요. 이름·연락처는 보내지 않아요.").font(.dearby(.footnote)).foregroundStyle(.secondary)
                }
            }
            .tint(DearbyStyle.teal).accessibilityIdentifier("settings-usage-stats")
            .onChange(of: analyticsEnabled) { _, on in AppAnalytics.setEnabled(on) }
            if let adConsent { adConsentRow(adConsent) }
        } header: {
            Text("개인정보").font(.dearby(.footnote))
        } footer: {
            if adConsent != nil { Text("광고 제공을 꺼도 이미 시작한 광고 기능은 앱을 완전히 닫을 때까지 남을 수 있어요.").font(.dearby(.footnote)) }
        }
    }
    /// On asks with the consent sheet and saves only on 동의하고 계속; off withdraws at once.
    private func adConsentRow(_ adConsent: AdConsentModel) -> some View {
        Toggle(isOn: Binding(get: { adConsent.agreed ?? false }, set: { on in Task { on ? await adConsent.turnOn() : await adConsent.turnOff() } })) {
            VStack(alignment: .leading, spacing: 2) {
                Text("광고 목적 정보 제공")
                Text("끄면 광고가 보이지 않아요").font(.dearby(.footnote)).foregroundStyle(.secondary)
            }
        }
        .tint(DearbyStyle.teal).disabled(adConsent.agreed == nil).accessibilityIdentifier("settings-ad-consent")
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
        let hadPasskeys = !model.passkeys.isEmpty
        Task {
            let result = await account.deleteAccount()
            if result != .failed { await model.load() }
            model.notice = result.notice(hadPasskeys: hadPasskeys)
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
