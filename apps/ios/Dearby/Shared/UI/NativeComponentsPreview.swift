import SwiftUI

/// Static fixtures only: previews do not resolve app services or models.
private struct NativeComponentsPreview: View {
    var body: some View {
        List {
            Section("정보") {
                InformationRow(title: "항목", value: "긴 내용도 시스템 글자 크기에 맞춰 여러 줄로 표시합니다.", systemImage: "text.alignleft")
                MetadataRow(systemImage: "calendar", text: "2026.9.15 14:00 – 2026.9.15 16:00 (한국 시간)")
                MetadataRow(systemImage: "mappin.and.ellipse", text: "온라인 또는 긴 이름의 행사 장소")
                StatusMessage(text: "추가 확인이 필요해요")
            }
            Section("동작") {
                PrimaryButton(action: {}) { Label("주요 동작", systemImage: "heart") }
                SecondaryButton(action: {}) { Text("보조 동작") }
                PrimaryButton(action: {}) { Text("사용할 수 없는 동작") }.disabled(true)
                Button("삭제", role: .destructive, action: {})
            }
            Section("빈 상태") {
                ContentUnavailableView("내용이 없어요", systemImage: "tray")
            }
        }
    }
}

#Preview("Native · Light") { NativeComponentsPreview().preferredColorScheme(.light) }
#Preview("Native · Dark") { NativeComponentsPreview().preferredColorScheme(.dark) }
#Preview("Native · Accessibility") {
    NativeComponentsPreview().environment(\.dynamicTypeSize, .accessibility5)
}
