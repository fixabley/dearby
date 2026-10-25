import SwiftUI

/// What an account tab shows while signed in without the current required consent (contract "개인정보 동의" 2026-10-25): one centred
/// message whose button opens the app's consent sheet. Browsing and activity details stay usable without it.
struct ConsentRequiredView: View {
    let ask: () -> Void
    var body: some View {
        CenteredMessage(symbol: "checkmark.shield", title: "동의가 필요해요",
                        message: "개인정보 수집·이용과 이용약관에 동의하면 프로필·명함·즐겨찾기 같은 계정 기능을 쓸 수 있어요.",
                        primary: MessageAction("동의하기", identifier: "consent-ask", action: ask))
    }
}
