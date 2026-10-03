import SwiftUI

// Preserve the application sheet, with local guidance instead of an embedded browser.
struct DemoApplicationView: View {
    let title: String
    let url: URL?
    let applied: Bool
    let complete: () -> Void
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Text("예시 신청 화면 · 실제 접수는 이루어지지 않아요.")
                    .font(.caption).padding(10).frame(maxWidth: .infinity).background(.bar)
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        Text(title).font(.title2.bold())
                        Text("신청 버튼과 완료 표시를 체험해 보세요. 기록은 이번 앱 실행 중에만 유지됩니다.")
                        if let url {
                            Link(destination: url) { Label("예시 링크를 외부 브라우저로 열기", systemImage: "arrow.up.right") }
                                .frame(minHeight: 44)
                            Text(url.absoluteString).font(.caption).foregroundStyle(.secondary)
                        }
                        Text("example.com은 예시 주소이며 실제 신청 사이트가 아닙니다.")
                            .font(.caption).foregroundStyle(.secondary)
                    }.padding(20)
                }
            }
            .safeAreaInset(edge: .bottom) {
                Button(applied ? "데모 신청 완료 확인" : "데모 신청 완료로 표시") { complete(); dismiss() }
                    .buttonStyle(DearbyButtonStyle()).padding(20)
            }
            .navigationTitle("신청 (예시)").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        }
    }
}
