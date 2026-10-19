import SwiftUI

/// 입력 화면 루트의 키보드 내리기(contract "키보드 내리기와 터치 충돌 점검"·"키보드의 내리기 버튼" 2026-10-20, SwiftUI만): 스크롤하면 손가락을 따라
/// 내려가고(`.scrollDismissesKeyboard(.interactively)`) 키보드 툴바의 `완료`가 화면의 `@FocusState`를 비운다(`done`). 빈 곳 탭으로 내리는 루트 제스처는 두지 않는다 —
/// 입력 행 전체가 그 필드에 포커스를 주므로(`DearbyFormRow`) 루트 제스처가 받을 영역이 없고, 빈 곳 탭 내리기는 iOS 관례가 아니다.
/// 화면마다 한 번만 붙인다(안쪽 위젯에도 붙이면 `완료`가 두 번 보인다).
struct DearbyDismissKeyboard: ViewModifier {
    let done: () -> Void
    func body(content: Content) -> some View {
        content.scrollDismissesKeyboard(.interactively)
            .toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("완료", action: done).accessibilityIdentifier("keyboard-done") } }
    }
}

extension View {
    func dearbyDismissKeyboard(done: @escaping () -> Void) -> some View { modifier(DearbyDismissKeyboard(done: done)) }
}
