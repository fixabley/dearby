import SwiftUI

/// Applied activities as progress tracks (contract "내 활동 진행 트랙 — 2026-10-18"): `진행 중` first, nearest next stage on top,
/// `끝난 활동` (every stage past, or a 탈락 mark) collapsed below. Stage-less activities keep the plain row with the participation mark.
/// With nothing applied the whole page is one centred message (contract "비스크롤 화면 배치·CTA"). The bar is the system navigation bar
/// (contract "iOS 상단 바는 시스템 내비게이션 바"·"상단 바 구성" 2026-10-20): inline title, 설정·알림 on the trailing side. The old `예시` badge is gone and
/// the `더 보기` menu stays hidden: there is no example dataset for a `예시 데이터 보기/숨기기` item to toggle (내 활동 is the user's own applied marks).
struct MyActivitiesPage: View {
    let state: ActivityViewModel
    @Binding var path: NavigationPath
    let explore: () -> Void
    @State private var showEnded = false
    var body: some View {
        let now = Date()
        let applied = state.appliedProgress(now: now)
        let active = applied.filter { !($0.progress?.ended ?? false) }
        let ended = applied.filter { $0.progress?.ended ?? false }
        Group {
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
        // `내 활동에서 보기` (contract "내 활동 직접 추가"): open that activity here — its track when it has stages, else the plain detail.
        .onChange(of: state.showInMine, initial: true) { _, id in
            guard let id, let activity = state.activity(id) else { return }
            state.showInMine = nil
            if state.progress(activity, now: Date()) != nil { path.append(ActivityProgressRoute(activityID: id)) } else { path.append(id) }
        }
        .scrollContentBackground(.hidden).background(Color(.systemGroupedBackground).ignoresSafeArea())
        .tint(DearbyStyle.teal).navigationTitle("내 활동").navigationBarTitleDisplayMode(.inline)
        .toolbar { TabRootToolbar() }
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
