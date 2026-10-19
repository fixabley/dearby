import SwiftUI

/// The consent gate before profile entry (contract "개인정보 동의", 2026-10-19): two required switches whose …보기 rows open
/// the web policy pages in-app, and 동의하고 계속 only once both are on. An inset-grouped `Form` (contract "폼은 플랫폼 기본 컴포넌트"),
/// full screen, no tab bar, like the first-profile funnel.
struct ConsentPage: View {
    let state: ProfileModel
    @State private var privacy = false
    @State private var terms = false
    @State private var opening: URL?
    @State private var saving = false
    @State private var error: String?
    private var ready: Bool { privacy && terms && !saving }
    var body: some View {
        Form {
            Section {
                Toggle("개인정보 수집·이용에 동의합니다", isOn: $privacy).accessibilityLabel("개인정보 수집·이용에 동의합니다, 필수")
                link("개인정보 처리방침 보기", page: "privacy")
                Toggle("이용약관에 동의합니다", isOn: $terms).accessibilityLabel("이용약관에 동의합니다, 필수")
                link("이용약관 보기", page: "terms")
            } header: {
                VStack(alignment: .leading, spacing: 6) {
                    Text("시작하기 전에 동의가 필요해요").font(.dearby(.title3).bold()).foregroundStyle(.primary)
                    Text("프로필에 적는 이름·연락처·이력을 저장하고 명함으로 보여 주기 위해 필요해요. 동의 내용과 버전은 계정에 기록돼요.").font(.dearby(.footnote))
                }.padding(.bottom, 8)
            } footer: {
                VStack(alignment: .leading, spacing: 4) {
                    Text("두 항목 모두 필수예요.")
                    if let error { DearbyFieldError(text: error) }
                }.font(.dearby(.footnote))
            }
        }
        .font(.dearby(.body))
        .safeAreaInset(edge: .bottom) {
            Button { agree() } label: {
                Group { if saving { ProgressView().tint(DearbyStyle.onTeal) } else { Text("동의하고 계속") } }.frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent).controlSize(.large).font(.dearby(.body).weight(.semibold)).disabled(!ready)
            .accessibilityIdentifier("consent-continue")
            .padding(.horizontal, 20).padding(.vertical, 12).background(.bar)
        }
        .background(Color(.systemGroupedBackground))
        .sheet(item: $opening) { url in SafariView(url: url).ignoresSafeArea() }
    }
    private func link(_ title: String, page: String) -> some View {
        Button { opening = state.webOrigin.appending(path: page) } label: {
            Label(title, systemImage: "safari").font(.dearby(.subheadline))
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
