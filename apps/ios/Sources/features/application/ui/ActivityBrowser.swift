import SwiftUI
import SafariServices

struct ActivityBrowser: View {
    let url: URL
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        VStack(spacing: 0) {
            Text("로그인이 안 되면 브라우저 메뉴에서 Safari로 열어 주세요. 신청 완료는 자동 확인하지 않아요.")
                .font(.caption).padding(10).frame(maxWidth: .infinity).background(.bar)
            SafariContent(url: url, close: { dismiss() })
        }
    }
}
private struct SafariContent: UIViewControllerRepresentable {
    let url: URL
    let close: () -> Void
    func makeCoordinator() -> Coordinator { Coordinator(close: close) }
    func makeUIViewController(context: Context) -> SFSafariViewController {
        let controller = SFSafariViewController(url: url)
        controller.delegate = context.coordinator
        controller.preferredControlTintColor = UIColor(red: 0, green: 0.36, blue: 0.34, alpha: 1)
        return controller
    }
    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
    final class Coordinator: NSObject, SFSafariViewControllerDelegate {
        let close: () -> Void
        init(close: @escaping () -> Void) { self.close = close }
        func safariViewControllerDidFinish(_ controller: SFSafariViewController) { close() }
    }
}
