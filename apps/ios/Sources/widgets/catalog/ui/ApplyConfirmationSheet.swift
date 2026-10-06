import SwiftUI

/// 공식 사이트에서 돌아왔을 때 묻는 "신청하셨나요?" 시트의 내용. 화면이 시트로 띄우고 세 선택을 받는다.
/// 신청 표시는 사용자가 직접 남기는 기록이며 주최 측 접수 확인이 아니라는 점을 함께 보인다.
struct ApplyConfirmationSheet: View {
    let activityTitle: String
    let applied: () -> Void
    let notYet: () -> Void
    let neverAsk: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Image(systemName: "checkmark.circle").font(.dearby(.title2)).foregroundStyle(DearbyStyle.teal)
                .frame(width: 48, height: 48).background(DearbyStyle.mint, in: Circle()).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 6) {
                Text("신청하셨나요?").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.ink).accessibilityAddTraits(.isHeader)
                Text(activityTitle).font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink)
                Text("신청했다면 내 활동에 표시해 둘게요. 이 표시는 직접 남기는 기록이며 주최 측 접수 확인은 아니에요.")
                    .font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).fixedSize(horizontal: false, vertical: true)
            }
            VStack(spacing: 10) {
                Button("신청했어요", action: applied).buttonStyle(DearbyButtonStyle())
                Button("아직이에요", action: notYet).buttonStyle(DearbyButtonStyle(outlined: true))
                Button("다시 묻지 않기", action: neverAsk).font(.dearby(.subheadline).weight(.semibold))
                    .foregroundStyle(DearbyStyle.quiet).frame(maxWidth: .infinity, minHeight: 44)
            }
        }.padding(20)
    }
}

#if DEBUG
#Preview("신청 확인") { ApplyConfirmationSheet(activityTitle: "주말 데이터 분석 스터디", applied: {}, notYet: {}, neverAsk: {}) }
#endif
