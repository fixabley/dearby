import SwiftUI

/// 입력 화면 루트의 키보드 내리기(contract "폼은 플랫폼 기본 컴포넌트" → "키보드 내리기"): 스크롤하면 손가락을 따라 내려가고
/// (`.scrollDismissesKeyboard(.interactively)`), 빈 곳을 누르면 첫 응답자를 내려 `@FocusState`도 `nil`이 된다.
/// 배경 전용 탭 제스처(`onTapGesture`, `simultaneousGesture` 아님)라 버튼·링크·필드·토글의 탭은 그대로 전달된다.
struct DearbyDismissKeyboard: ViewModifier {
    func body(content: Content) -> some View {
        content.scrollDismissesKeyboard(.interactively)
            .onTapGesture { UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil) }
    }
}

extension View {
    func dearbyDismissKeyboard() -> some View { modifier(DearbyDismissKeyboard()) }
}
