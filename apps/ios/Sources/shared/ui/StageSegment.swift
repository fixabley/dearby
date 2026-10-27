import SwiftUI

/// One segment of a 진행 트랙: 지난 = 청록 채움, 지금 = 연청록 + 청록 테두리, 다음 = 회색, 탈락 = 빨강. Shared by 내 활동's `StageTrack` and the
/// Live Activity's session track (contract "행사 당일 진행 중 표시 › 표시 디자인 — 2026-10-27"), which compiles this file and `DearbyStyle`.
struct StageSegment: View {
    enum State { case past, current, upcoming, failed }
    let state: State
    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 3)
        switch state {
        case .past: shape.fill(DearbyStyle.teal)
        case .current: shape.fill(DearbyStyle.mint).overlay(shape.stroke(DearbyStyle.teal, lineWidth: 1.5))
        case .upcoming: shape.fill(Color(.opaqueSeparator))
        case .failed: shape.fill(DearbyStyle.danger)
        }
    }
}
