import SwiftUI

/// Applied activities as progress tracks (contract "내 활동 진행 트랙 — 2026-10-18"): `진행 중` first, nearest next stage on top,
/// `끝난 활동` (every stage past, or a 탈락 mark) collapsed below. Stage-less activities keep the plain row with the participation mark.
/// With nothing applied the title line stays and the rest is one centred message (contract "비스크롤 화면 배치·CTA").
struct MyActivitiesPage: View {
    let state: ActivityViewModel
    let explore: () -> Void
    @State private var showEnded = false
    var body: some View {
        let now = Date()
        let applied = state.appliedProgress(now: now)
        let active = applied.filter { !($0.progress?.ended ?? false) }
        let ended = applied.filter { $0.progress?.ended ?? false }
        VStack(spacing: 0) {
            PageTitle("내 활동") { DearbyBadge(title: "예시") }
            if applied.isEmpty {
                CenteredMessage(symbol: "checklist", title: "신청한 활동이 없어요", message: "발견에서 관심 있는 활동을 신청해 보세요.",
                                primary: MessageAction("활동 둘러보기", identifier: "explore", action: explore))
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("단계 표시와 참여 확정 표시는 내가 남기는 예시 표시예요. 주최 측 결과가 아니에요.")
                            .font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
                        Text("진행 중").font(.dearby(.headline)).accessibilityAddTraits(.isHeader)
                        if active.isEmpty {
                            Text("진행 중인 활동이 없어요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
                        }
                        ForEach(active) { row($0, now: now) }
                        if !ended.isEmpty {
                            DisclosureGroup(isExpanded: $showEnded) {
                                VStack(spacing: 16) { ForEach(ended) { row($0, now: now) } }.padding(.top, 12)
                            } label: {
                                Text("끝난 활동 \(ended.count)").font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink)
                            }.tint(DearbyStyle.quiet)
                        }
                    }.padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 20)
                }
            }
        }
        .navigationDestination(for: String.self) { id in ActivityDetailView(state: state, activityID: id) }
        .navigationDestination(for: ActivityProgressRoute.self) { route in ActivityProgressPage(state: state, activityID: route.activityID) }
        .scrollContentBackground(.hidden).background(Color(.systemGroupedBackground))
        .tint(DearbyStyle.teal).navigationTitle("")
        .navigationBarTitleDisplayMode(.inline).toolbar(.hidden, for: .navigationBar)
    }
    @ViewBuilder private func row(_ item: AppliedActivity, now: Date) -> some View {
        if let progress = item.progress {
            ActivityProgressRow(activity: item.activity, progress: progress, now: now)
        } else {
            ActivityRow(activity: item.activity) {
                DearbyBadge(title: state.confirmedIDs.contains(item.id) ? "참여 확정" : "신청함")
                ConfirmToggle(state: state, activityID: item.id)
            }
        }
    }
}
