import SwiftUI

/// 미확인: no admin-verified evidence yet (contract "이력"). Verified entries get a small check.
struct HistoryBadge: View {
    let verified: Bool
    var body: some View {
        if verified {
            Label("확인됨", systemImage: "checkmark.circle").font(.dearby(.caption2).weight(.semibold)).foregroundStyle(DearbyStyle.teal)
        } else {
            Text("미확인").font(.dearby(.caption2).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                .padding(.horizontal, 7).padding(.vertical, 2).background(DearbyStyle.muted, in: Capsule())
                .accessibilityLabel("미확인, 인증자료가 확인되지 않은 이력")
        }
    }
}
