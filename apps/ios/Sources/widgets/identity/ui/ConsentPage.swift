import SwiftUI

/// The consent gate before profile entry (contract "개인정보 동의", 2026-10-19): two required checkbox rows (`DearbyCheckRow`, multi-line labels —
/// contract "토글 라벨은 바로 옆" 2026-10-20) whose …보기 rows open the web policy pages in-app, and 동의하고 계속 only once both are on.
/// An inset-grouped `Form` (contract "폼은 플랫폼 기본 컴포넌트"); the head above the rows is the centred state symbol·title·explanation
/// (contract "비스크롤 화면 배치·CTA") and the CTA is the full-width bottom button.
/// Presented as a sheet it can be pulled down or closed with 닫기 (contract "동의 시트 닫기" 2026-10-20): closing means not consented, so
/// saving stays blocked and the server's 403 brings the page back. In the tab root (full screen) there is nothing to dismiss, so no 닫기 shows.
struct ConsentPage: View {
    let state: ProfileModel
    @State private var privacy = false
    @State private var terms = false
    @State private var opening: URL?
    @State private var saving = false
    @State private var error: String?
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
                    Text("시작하기 전에 동의가 필요해요").font(.dearby(.title2).bold()).foregroundStyle(.primary).multilineTextAlignment(.center)
                        .accessibilityAddTraits(.isHeader)
                    Text("프로필에 적는 이름·연락처·이력을 저장하고 명함으로 보여 주기 위해 필요해요. 동의 내용과 버전은 계정에 기록돼요.")
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
            Button { agree() } label: {
                Group { if saving { ProgressView().tint(DearbyStyle.onTeal) } else { Text("동의하고 계속") } }.frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent).controlSize(.large).tint(DearbyStyle.tealFill).font(.dearby(.body).weight(.semibold)).disabled(!ready)
            .accessibilityIdentifier("consent-continue")
            .padding(.horizontal, 24).padding(.vertical, 12).background(.bar)
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
