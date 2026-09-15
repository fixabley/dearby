import SwiftUI

/// Display-only save action; the caller owns saved state and accessibility identity.
struct NoticeCardSaveButton: View {
    let saved: Bool
    let organizationName: String?
    let onSave: () -> Void

    var body: some View {
        if let organizationName {
            VStack(alignment: .leading, spacing: NativeSpacing.related) {
                Text(organizationName).font(.subheadline.weight(.semibold))
                PrimaryButton(action: onSave) {
                    Label(saved ? "저장됨 · \(organizationName)" : "\(organizationName) 저장",
                          systemImage: saved ? "heart.fill" : "heart")
                        .labelStyle(.iconOnly)
                        .frame(maxWidth: .infinity).fixedSize(horizontal: false, vertical: true)
                }
                .accessibilityValue(saved ? "저장됨" : "저장 안 됨")
            }
        } else {
            StatusMessage(text: "저장할 조직 확인 중", systemImage: "heart")
        }
    }
}
