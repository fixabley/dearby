import SwiftUI

/// Display-only save action; the caller owns saved state and accessibility identity.
struct SaveOrganizationButton: View {
    let saved: Bool
    let organizationName: String
    let onSave: () -> Void

    var body: some View {
        Button(action: onSave) {
            Label(saved ? "저장됨 · \(organizationName)" : "\(organizationName) 저장",
                  systemImage: saved ? "heart.fill" : "heart")
                .frame(maxWidth: .infinity).lineLimit(2)
        }
        .buttonStyle(.borderedProminent)
    }
}
