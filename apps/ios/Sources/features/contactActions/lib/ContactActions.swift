import UIKit

@MainActor enum ContactActions {
    static func perform(_ contact: ContactModel) {
        switch contact.action {
        case .open(let url): UIApplication.shared.open(url)
        case .copy(let value): UIPasteboard.general.string = value
        case .unavailable: break
        }
    }
}
