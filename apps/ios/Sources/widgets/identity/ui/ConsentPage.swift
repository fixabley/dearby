import SwiftUI

/// The consent gate before profile entry (contract "개인정보 동의", 2026-10-19): two required checkbox rows (`DearbyCheckRow`, multi-line labels —
/// contract "토글 라벨은 바로 옆" 2026-10-20) whose …보기 rows open the web policy pages in-app, and 동의하고 계속 only once both are on.
/// An inset-grouped `Form` (contract "폼은 플랫폼 기본 컴포넌트"); the head above the rows is the centred state symbol·title·explanation
/// (contract "비스크롤 화면 배치·CTA") and the CTA is the full-width bottom button.
/// Presented as a sheet it can be pulled down or closed with 닫기 (contract "동의 시트 닫기" 2026-10-20): closing means not consented, so
/// the session stays and the app asks again (launch, foreground, an account feature; contract "개인정보 동의" 2026-10-25). Under the CTA, an account
/// right after sign-up (no profile saved) gets `가입 취소` — the account is deleted through the usual deletion — and one asked again for a new version `로그아웃`.
struct ConsentPage: View {
    let state: ProfileModel
    @State private var privacy = false
    @State private var terms = false
    @State private var opening: URL?
    @State private var saving = false
    @State private var error: String?
    @State private var confirmingCancel = false
    @State private var confirmingSignOut = false
    /// Right after sign-up: nothing saved yet, so leaving cancels the sign-up instead of signing out.
    private var freshAccount: Bool { state.profile?.version == 0 }
    /// Agreed to an earlier version before (the profile's `consent` is set): the title says the policy changed (2026-10-26).
    private var renewal: Bool { state.profile?.consent != nil }
    @Environment(\.dismiss) private var dismiss
    @Environment(\.isPresented) private var isPresented
    private var ready: Bool { privacy && terms && !saving }
    var body: some View {
        Form {
            Section {
                DearbyCheckRow(isOn: $privacy) { Text("개인정보 수집·이용에 동의합니다") }.accessibilityLabel("개인정보 수집·이용에 동의합니다, 필수")
                link("개인정보 처리방침 보기", page: "privacy")
                DearbyCheckRow(isOn: $terms) { Text("이용약관에 동의합니다") }.accessibilityLabel("이용약관에 동의합니다, 필수")
                link("이용약관 보기", page: "terms")
            } header: {
                VStack(spacing: 12) {
                    StateGlyph(symbol: "checkmark.circle").padding(.bottom, 8)
                    Text(renewal ? "개인정보 처리방침이 바뀌었어요" : "시작하기 전에 동의가 필요해요").font(.dearby(.title2).bold()).foregroundStyle(.primary).multilineTextAlignment(.center)
                        .accessibilityAddTraits(.isHeader)
                    Text("프로필(이름·연락처·이력, 고른 경우 사진·소개 링크)과 내 활동·즐겨찾기 같은 계정 기록을 저장하는 데 필요해요. 광고 목적 정보 제공은 여기 포함되지 않고 따로 묻는 선택 항목이에요. 동의 내용과 버전은 계정에 기록돼요.")
                        .font(.dearby(.body)).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }.frame(maxWidth: .infinity).textCase(nil).padding(.top, 24).padding(.bottom, 16)
            } footer: {
                VStack(alignment: .leading, spacing: 4) {
                    Text("두 항목 모두 필수예요.")
                    if let error { DearbyFieldError(text: error) }
                }.font(.dearby(.subheadline))
            }
        }
        .font(.dearby(.body))
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 4) {
                Button { agree() } label: {
                    Group { if saving { ProgressView().tint(DearbyStyle.onTeal) } else { Text("동의하고 계속") } }.frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent).controlSize(.large).tint(DearbyStyle.tealFill).font(.dearby(.body).weight(.semibold)).disabled(!ready)
                .accessibilityIdentifier("consent-continue")
                if freshAccount {
                    Button("가입 취소", role: .destructive) { confirmingCancel = true }.accessibilityIdentifier("consent-cancel-signup")
                        .frame(minHeight: 44).disabled(saving)
                } else {
                    Button("로그아웃") { confirmingSignOut = true }.accessibilityIdentifier("consent-sign-out").frame(minHeight: 44).disabled(saving)
                }
            }
            .font(.dearby(.body)).padding(.horizontal, 24).padding(.vertical, 12).background(.bar)
        }
        .confirmationDialog("가입을 취소할까요?", isPresented: $confirmingCancel, titleVisibility: .visible) {
            Button("가입 취소", role: .destructive) { leave { await state.account.deleteAccount() != .failed } }
            Button("계속 가입", role: .cancel) {}
        } message: { Text("가입을 취소하면 방금 만든 계정이 삭제돼요.") }
        .confirmationDialog("로그아웃할까요?", isPresented: $confirmingSignOut, titleVisibility: .visible) {
            Button("로그아웃", role: .destructive) { leave { await state.account.signOut(); return true } }
            Button("취소", role: .cancel) {}
        }
        .background(Color(.systemGroupedBackground))
        .interactiveDismissDisabled(false)
        .toolbar {
            if isPresented {
                ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() }.accessibilityIdentifier("consent-close") }
            }
        }
        .sheet(item: $opening) { url in SafariView(url: url).ignoresSafeArea() }
    }
    private func link(_ title: String, page: String) -> some View {
        Button { opening = state.webOrigin.appending(path: page) } label: {
            Label(title, systemImage: "arrow.up.right").font(.dearby(.subheadline)).frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
        }
    }
    /// 가입 취소 / 로그아웃: once signed out the app's sheet closes by itself (it shows only while signed in without consent).
    private func leave(_ action: @escaping () async -> Bool) {
        saving = true
        Task {
            if await action() { await state.load() } else { error = "가입을 취소하지 못했어요. 잠시 후 다시 시도해 주세요." }
            saving = false
        }
    }
    private func agree() {
        guard ready else { return }
        saving = true
        Task {
            error = await state.consent()
            saving = false
        }
    }
}

extension URL: @retroactive Identifiable { public var id: String { absoluteString } }
