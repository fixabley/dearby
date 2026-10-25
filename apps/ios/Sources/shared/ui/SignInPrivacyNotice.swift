import SwiftUI

/// The one-line notice right under the Apple·Google buttons (contract "애플 로그인" 2026-10-25 사용자 결정): sign-in identifiers are handled
/// under the privacy policy, no separate consent. The link opens the policy in Safari.
struct SignInPrivacyNotice: View {
    var body: some View {
        Text("로그인하면 계정 식별을 위해 Apple·Google 계정 정보(이메일 등)를 [개인정보처리방침](https://dearby.wid.io.kr/privacy)에 따라 처리해요.")
            .font(.dearby(.footnote)).fontWeight(.regular).foregroundStyle(.secondary).tint(DearbyStyle.teal)
            .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
            .accessibilityIdentifier("sign-in-privacy-notice")
    }
}
