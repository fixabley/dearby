import SwiftUI

/// The consent gate before profile entry (contract "개인정보 동의", 2026-10-19): two required checks whose 보기 links open
/// the web policy pages in-app, and 동의하고 계속 only once both are on. Full screen, no tab bar, like the first-profile funnel.
struct ConsentPage: View {
    let state: ProfileModel
    @State private var privacy = false
    @State private var terms = false
    @State private var opening: URL?
    @State private var saving = false
    @State private var error: String?
    private var ready: Bool { privacy && terms && !saving }
    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("시작하기 전에 동의가 필요해요").font(.dearby(.title2).bold()).accessibilityAddTraits(.isHeader)
                    Text("프로필에 적는 이름·연락처·이력을 저장하고 명함으로 보여 주기 위해 필요해요. 동의 내용과 버전은 계정에 기록돼요.")
                        .font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
                    row("개인정보 수집·이용에 동의합니다 (필수)", on: $privacy, page: "privacy")
                    row("이용약관에 동의합니다 (필수)", on: $terms, page: "terms")
                    if let error { DearbyFieldError(text: error) }
                }.padding(24)
            }
            Button { agree() } label: { if saving { ProgressView().tint(.white) } else { Text("동의하고 계속") } }
                .buttonStyle(DearbyButtonStyle()).disabled(!ready).padding(.horizontal, 24).padding(.vertical, 12)
                .accessibilityIdentifier("consent-continue")
        }
        .background(Color(.systemBackground))
        .sheet(item: $opening) { url in SafariView(url: url).ignoresSafeArea() }
    }
    private func row(_ title: String, on: Binding<Bool>, page: String) -> some View {
        HStack(spacing: 12) {
            Toggle(title, isOn: on).toggleStyle(CheckToggleStyle()).font(.dearby(.body))
            Spacer(minLength: 0)
            Button("보기") { opening = state.webOrigin.appending(path: page) }
                .font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
                .accessibilityLabel(page == "privacy" ? "개인정보 처리방침 보기" : "이용약관 보기")
        }
        .padding(16).background(DearbyStyle.muted, in: RoundedRectangle(cornerRadius: 12))
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

/// A checkbox-looking toggle (SwiftUI has none on iOS): a teal checkmark circle when on, a line circle when off.
private struct CheckToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button { configuration.isOn.toggle() } label: {
            HStack(spacing: 10) {
                Image(systemName: configuration.isOn ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 24)).foregroundStyle(configuration.isOn ? DearbyStyle.teal : DearbyStyle.line)
                configuration.label.foregroundStyle(DearbyStyle.ink).multilineTextAlignment(.leading)
            }
        }
        .buttonStyle(.plain).frame(minHeight: 44).accessibilityAddTraits(configuration.isOn ? .isSelected : [])
    }
}

extension URL: @retroactive Identifiable { public var id: String { absoluteString } }
