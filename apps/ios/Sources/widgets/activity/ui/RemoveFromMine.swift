import SwiftUI

extension View {
    /// The one confirmation before `내 활동에서 빼기` (contract "내 활동 직접 추가" — "빼기는 눈에 보이게" 2026-10-20): removing also
    /// clears the participation mark and stage marks. `then` runs after removal (the progress page pops back to the list).
    func removeFromMineDialog(_ activityID: Binding<String?>, state: ActivityViewModel, then: @escaping () -> Void = {}) -> some View {
        confirmationDialog("내 활동에서 뺄까요?", isPresented: Binding(get: { activityID.wrappedValue != nil },
                                                                 set: { if !$0 { activityID.wrappedValue = nil } }),
                           titleVisibility: .visible, presenting: activityID.wrappedValue) { id in
            Button("내 활동에서 빼기", role: .destructive) { state.apply(id, false); then() }
            Button("취소", role: .cancel) {}
        } message: { _ in Text("단계 표시와 참여 확정도 함께 지워져요.") }
    }
}
