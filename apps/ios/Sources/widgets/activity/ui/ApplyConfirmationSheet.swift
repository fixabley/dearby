import SwiftUI

/// 공식 사이트에서 돌아왔을 때 묻는 "신청하셨나요?" 시트의 내용(contract "비스크롤 화면 배치·CTA": 가운데 상태 심볼·제목·설명, 아래 전체 너비 주 버튼과 텍스트 보조 버튼).
/// 신청 표시는 사용자가 직접 남기는 기록이며 주최 측 접수 확인이 아니라는 점을 함께 보인다.
struct ApplyConfirmationSheet: View {
    let activityTitle: String
    let applied: () -> Void
    let notYet: () -> Void
    let neverAsk: () -> Void
    var body: some View {
        CenteredMessage(symbol: "checkmark.circle", title: "신청하셨나요?",
                        message: "\(activityTitle)\n신청했다면 내 활동에 표시해 둘게요. 직접 남기는 기록이며 주최 측 접수 확인은 아니에요.") {
            Button(action: applied) { Text("신청했어요").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent)
            Button(action: notYet) { Text("아직이에요").frame(maxWidth: .infinity, minHeight: 44) }.buttonStyle(.borderless).tint(DearbyStyle.teal)
            Button(action: neverAsk) { Text("다시 묻지 않기").frame(maxWidth: .infinity, minHeight: 44) }.buttonStyle(.borderless).tint(DearbyStyle.teal)
        }
        .padding(.top, 20).background(Color(.systemBackground))
    }
}

/// 지난 선발 단계 통과 제안 시트(contract "지난 선발 단계 통과 제안" 2026-10-26): 내 활동에 넣은 직후, 이미 지난 선발 단계가 있으면 한 번 묻는다.
/// 신청 확인 시트와 같은 배치(`CenteredMessage`), 주 버튼 `통과로 표시`·보조 `그대로 두기`.
struct AutopassSheet: View {
    let suggestion: AutopassSuggestion
    let mark: () -> Void
    let keep: () -> Void
    var body: some View {
        CenteredMessage(symbol: "checkmark.circle", title: "지난 선발 단계를 통과로 표시할까요?", message: suggestion.message,
                        primary: MessageAction("통과로 표시", identifier: "autopass-mark", action: mark),
                        secondary: MessageAction("그대로 두기", identifier: "autopass-keep", action: keep))
            .padding(.top, 20).background(Color(.systemBackground))
    }
}

extension View {
    /// 내 활동 prompts (`신청하셨나요?`, then the 통과 제안 once that sheet is gone), presented here only while `isTop` (`promptLayer`).
    func activityPrompts(_ state: ActivityViewModel, isTop: Bool) -> some View {
        sheet(item: Binding(get: { isTop ? state.asking : nil }, set: { if $0 == nil { state.answer(.notYet) } }),
              onDismiss: state.applySheetDismissed) { activity in
            ApplyConfirmationSheet(activityTitle: activity.title, applied: { state.answer(.applied) },
                                   notYet: { state.answer(.notYet) }, neverAsk: { state.answer(.neverAsk) })
                .presentationDetents([.medium])
        }
        .sheet(item: Binding(get: { isTop ? state.autopass : nil }, set: { if $0 == nil { state.skipAutopass() } })) { suggestion in
            AutopassSheet(suggestion: suggestion, mark: state.acceptAutopass, keep: state.skipAutopass).presentationDetents([.medium])
        }
    }
}

#if DEBUG
#Preview("신청 확인") { ApplyConfirmationSheet(activityTitle: "주말 데이터 분석 스터디", applied: {}, notYet: {}, neverAsk: {}) }
#endif
