import SwiftUI

/// 로그인·동의 시트와 알림을 맨 위 화면에서 띄우기(contract "트랙·세션 시간표" 2026-10-26 모달 시트): 앱 루트가 띄우면 위에 시트가 떠 있을 때
/// 무시되므로, 앱은 그 표시를 `promptHost`로 넘기고 루트와 시트 내용은 `.promptLayer()`를 단다. 가장 나중에 나타난 층 하나만 띄워 같은 화면이
/// 두 번 뜨지 않고, 아래 화면(보던 시간표)은 그대로 남는다. 앱이 `promptHost`를 넘기지 않은 미리보기에서는 아무것도 달지 않는다.
@MainActor @Observable final class PromptLayers {
    private var stack: [UUID] = []
    private(set) var top: UUID?
    func push(_ id: UUID) { stack.append(id); top = id }
    func pop(_ id: UUID) { stack.removeAll { $0 == id }; top = stack.last }
}
struct PromptHost {
    let layers: PromptLayers
    /// The app's sheets and alerts attached to a layer's content; `isTop` false keeps them hidden there.
    let attach: @MainActor (_ content: AnyView, _ isTop: Bool) -> AnyView
}
extension EnvironmentValues {
    @Entry var promptHost: PromptHost?
}
extension View {
    func promptLayer() -> some View { modifier(PromptLayerModifier()) }
}
private struct PromptLayerModifier: ViewModifier {
    @Environment(\.promptHost) private var host
    @State private var id = UUID()
    func body(content: Content) -> some View {
        if let host {
            host.attach(AnyView(content), host.layers.top == id)
                .onAppear { host.layers.push(id) }
                .onDisappear { host.layers.pop(id) }
        } else {
            content
        }
    }
}
