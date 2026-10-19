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

#if DEBUG
#Preview("신청 확인") { ApplyConfirmationSheet(activityTitle: "주말 데이터 분석 스터디", applied: {}, notYet: {}, neverAsk: {}) }
#endif
