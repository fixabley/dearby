import SwiftUI

/// Display-only save action; the caller owns saved state and accessibility identity.
struct NoticeCardSaveButton: View {
    let saved: Bool
    let organizationName: String
    let onSave: () -> Void

    var body: some View {
        PrimaryButton(action: onSave) {
            Label(saved ? "저장됨 · \(organizationName)" : "\(organizationName) 저장",
                  systemImage: saved ? "heart.fill" : "heart")
                .frame(maxWidth: .infinity).fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityValue(saved ? "저장됨" : "저장 안 됨")
    }
}
