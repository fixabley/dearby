import ActivityKit
import SwiftUI

/// 행사 안내 (contract "행사 당일 진행 중 표시", 2026-10-27 사용자 결정: "네이버지도의 길찾기 안내 시작처럼"): on the 오늘 카드 and the timetable's
/// 내 시간표, while today's plan (`LiveSessionModel.plan`) is this activity's and a session is not over. Off: `행사 안내 시작` starts the Live Activity
/// at once. On: `안내 중 · 종료` ends it. Swiped away or ended by iOS (`.dismissed`): `안내 다시 시작`. With 실시간 현황 off in iOS 설정 a line says so.
struct LiveGuideButton: View {
    let activityID: String
    @Environment(LiveSessionModel.self) private var liveSession
    var body: some View {
        TimelineView(.everyMinute) { context in
            if let plan = liveSession.plan, plan.attributes.activityID == activityID, plan.open(at: context.date) {
                VStack(alignment: .leading, spacing: 6) {
                    button
                    if !ActivityAuthorizationInfo().areActivitiesEnabled {
                        Text("iOS 설정 › Dearby에서 실시간 현황을 켜면 잠금 화면과 Dynamic Island에 보여요")
                            .font(.dearby(.footnote)).foregroundStyle(.secondary)
                    }
                }
            }
        }
    }
    @ViewBuilder private var button: some View {
        switch liveSession.guide {
        case .on:
            Button { liveSession.stop() } label: {
                Label("안내 중 · 종료", systemImage: "figure.walk").font(.dearby(.subheadline).weight(.semibold)).frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered).controlSize(.large).tint(DearbyStyle.teal)
            .accessibilityHint("잠금 화면과 Dynamic Island의 행사 안내를 끝내요").accessibilityIdentifier("live-guide-stop")
        case .off, .dismissed:
            Button { liveSession.start() } label: {
                Label(liveSession.guide == .dismissed ? "안내 다시 시작" : "행사 안내 시작", systemImage: "figure.walk")
                    .font(.dearby(.subheadline).weight(.semibold)).frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent).controlSize(.large).tint(DearbyStyle.tealFill)
            .accessibilityHint("내 시간표 세션 진행과 이동을 잠금 화면과 Dynamic Island에 보여요").accessibilityIdentifier("live-guide-start")
        }
    }
}
