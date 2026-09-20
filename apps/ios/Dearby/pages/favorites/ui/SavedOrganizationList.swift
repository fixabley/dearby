import SwiftUI

/// Saved collection availability and on-device storage disclosure share the native presentation.
struct SavedOrganizationList<Content: View>: View {
    let isEmpty: Bool
    @ViewBuilder let content: () -> Content

    var body: some View {
        Group {
            if isEmpty {
                ContentUnavailableView("저장한 조직이 없어요", systemImage: "heart",
                    description: Text("발견 탭의 공고를 더블탭하면 조직이 여기에 저장돼요."))
            } else {
                content()
            }
        }
        .safeAreaInset(edge: .bottom) {
            Text("이 기기에 저장돼요").font(.caption).foregroundStyle(.secondary)
                .frame(maxWidth: .infinity).padding(NativeSpacing.related).background(NativeSurface.canvas)
        }
    }
}
