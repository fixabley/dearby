import SafariServices
import SwiftUI

/// In-app browser for policy pages (`SFSafariViewController`); the session is Safari's, nothing is shared with the app.
struct SafariView: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController {
        let controller = SFSafariViewController(url: url)
        controller.preferredControlTintColor = DearbyStyle.tealUIColor
        return controller
    }
    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}
