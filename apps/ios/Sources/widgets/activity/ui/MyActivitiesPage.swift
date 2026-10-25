import SwiftUI

/// Applied activities as progress tracks (contract "내 활동 진행 트랙 — 2026-10-18"): `진행 중` first, nearest next stage on top,
/// `끝난 활동` (every stage past, or a 탈락 mark) collapsed below. Stage-less activities keep the plain row with a `신청함` badge.
/// Rows are removed by a trailing swipe or the context menu (contract "내 활동 목록에서 밀어서 지우기" 2026-10-22); records whose activity
/// was deleted show as "삭제된 활동" with only 빼기. With nothing applied the whole page is one centred message (contract "비스크롤 화면 배치·CTA"). The bar is the system navigation bar
/// (contract "iOS 상단 바는 시스템 내비게이션 바"·"상단 바 구성" 2026-10-20): inline title, 설정·알림 on the trailing side. The old `예시` badge is gone and
/// the `더 보기` menu stays hidden: there is no example dataset for a `예시 데이터 보기/숨기기` item to toggle (내 활동 is the user's own applied marks).
/// The tab's top is a segmented `내 활동 | 즐겨찾기` in the bar's title place (contract "즐겨찾기(하트)" 2026-10-25, iOS `Picker(.segmented)`); the
/// app holds the choice (memory only) so a notification can open 즐겨찾기.
struct MyActivitiesPage: View {
    let state: ActivityViewModel
    @Binding var segment: MineSegment
    @Binding var path: NavigationPath
    let explore: () -> Void
    @State private var showEnded = false
    @State private var removing: String?
    var body: some View {
        Group {
            switch segment {
            case .mine: mine.analyticsScreen("my_activity")
            case .favorites: FavoritesPage(state: state, path: $path)
            }
        }
        .navigationDestination(for: String.self) { id in
            ActivityDetailView(state: state, activityID: id, source: segment == .favorites ? "favorites" : "my_activity")
        }
        .navigationDestination(for: ActivityProgressRoute.self) { route in ActivityProgressPage(state: state, activityID: route.activityID) }
        .navigationDestination(for: ProgramRoute.self) { route in ProgramDetailPage(state: state, program: route.program, path: $path) }
        // `내 활동으로` on a detail (contract "활동 상세 내 활동 행동 정리"): open that activity here — its track when it has stages, else the plain detail.
        .onChange(of: state.showInMine, initial: true) { _, id in
            guard let id, let activity = state.activity(id) else { return }
            state.showInMine = nil
            segment = .mine
            if state.progress(activity, now: Date()) != nil { path.append(ActivityProgressRoute(activityID: id)) } else { path.append(id) }
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .tint(DearbyStyle.teal).navigationTitle("내 활동").navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Picker("내 활동 구역", selection: $segment.reporting { AppAnalytics.event("menu_open", ["menu": $0 == .favorites ? "favorites" : "my_activity_list"]) }) {
                    ForEach(MineSegment.allCases, id: \.self) { Text($0.title).tag($0) }
                }.pickerStyle(.segmented).accessibilityIdentifier("mine-segment")
            }
            TabRootToolbar()
        }
    }
    private var mine: some View {
        let now = Date()
        let applied = state.appliedProgress(now: now)
        let active = applied.filter { !($0.progress?.ended ?? false) }
        let ended = applied.filter { $0.progress?.ended ?? false }
        return Group {
            if applied.isEmpty && state.deletedIDs.isEmpty {
                if state.mineLoading {
                    skeleton
                } else {
                    CenteredMessage(symbol: "checklist", title: "신청한 활동이 없어요", message: "발견에서 관심 있는 활동을 신청해 보세요.",
                                    primary: MessageAction("활동 둘러보기", identifier: "explore", action: explore))
                }
            } else {
                // A `List` so rows can be swiped away (contract "내 활동 목록에서 밀어서 지우기" 2026-10-22). The rows keep painting their
                // own inset-grouped cards, so the list draws no cell background or separators.
                List {
                    plain(Text("단계 상태는 내가 남기는 표시예요. 주최 측 결과가 아니에요.")
                        .font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet))
                    plain(Text("진행 중").font(.dearby(.headline)).accessibilityAddTraits(.isHeader))
                    if active.isEmpty {
                        plain(Text("진행 중인 활동이 없어요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet))
                    }
                    ForEach(active) { row($0, now: now) }
                    if !ended.isEmpty {
                        DisclosureGroup(isExpanded: $showEnded) {
                            ForEach(ended) { row($0, now: now) }
                        } label: {
                            Text("끝난 활동 \(ended.count)").font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink)
                        }
                        .tint(DearbyStyle.quiet).listRowBackground(Color.clear).listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 8, leading: 20, bottom: 8, trailing: 20))
                    }
                    ForEach(state.deletedIDs, id: \.self) { deleted($0) }
                }
                .listStyle(.plain).environment(\.defaultMinListRowHeight, 0)
                .refreshable { await state.syncMine() }
            }
        }
        .scrollContentBackground(.hidden)
        .removeFromMineDialog($removing, state: state)
    }
    /// Loading the account's list (contract "내용 로딩은 스켈레톤" 2026-10-22): progress rows filled with a sample activity.
    private var skeleton: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let progress = ActivityStages.build(Self.sample, marks: [:], now: .now) {
                    ForEach(0..<3, id: \.self) { _ in ActivityProgressRow(activity: Self.sample, progress: progress, now: .now) {} }
                }
            }.padding(20).dearbySkeleton()
        }.scrollDisabled(true)
    }
    private static let sample: ActivityModel = {
        func stage(_ id: String, _ title: String, _ kind: ActivityStageModel.Kind) -> ActivityStageModel {
            ActivityStageModel(id: id, title: title, kind: kind, start: nil, end: nil, allDay: true, dateLabel: "", timeZone: "Asia/Seoul")
        }
        return ActivityModel(id: "", title: "불러오는 활동 이름", summary: "", organization: "주최 기관", program: "프로그램 이름",
                             recruitmentStartAt: nil, recruitmentEndAt: nil, recruitmentStartDate: nil, recruitmentEndDateExclusive: nil,
                             applicationStage: stage("a", "지원", .deadline), stages: [stage("b", "면접", .interview)],
                             activityStage: stage("c", "활동", .event), officialUrl: "", imageUrl: nil)
    }()
    /// One full-width row without the list's own background, separator or chevron.
    private func plain(_ content: some View) -> some View {
        content.frame(maxWidth: .infinity, alignment: .leading)
            .listRowBackground(Color.clear).listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: 8, leading: 20, bottom: 8, trailing: 20))
    }
    /// Swipe from the trailing edge or the context menu offers 빼기; both go through the one removal confirmation (no full swipe, so the
    /// row never leaves before it is confirmed).
    private func removable(_ content: some View, id: String) -> some View {
        plain(content)
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                Button("빼기", systemImage: "trash", role: .destructive) { removing = id }
            }
            .contextMenu { Button("내 활동에서 빼기", systemImage: "trash", role: .destructive) { removing = id } }
    }
    private func row(_ item: AppliedActivity, now: Date) -> some View {
        removable(rowContent(item, now: now), id: item.id)
    }
    @ViewBuilder private func rowContent(_ item: AppliedActivity, now: Date) -> some View {
        if let progress = item.progress {
            ActivityProgressRow(activity: item.activity, progress: progress, now: now) {
                path.append(ActivityProgressRoute(activityID: item.id))
            }
        } else {
            ActivityRow(activity: item.activity, open: { path.append(item.id) }) { DearbyBadge(title: "신청함") }
        }
    }
    /// A record whose activity was deleted (contract "삭제된 활동"): nothing to open, only 빼기.
    private func deleted(_ id: String) -> some View {
        removable(HStack {
            Text("삭제된 활동").font(.dearby(.headline)).foregroundStyle(DearbyStyle.quiet)
            Spacer()
            Button("빼기", role: .destructive) { removing = id }.buttonStyle(.borderless).frame(minHeight: 44)
                .accessibilityIdentifier("deleted-remove-\(id)")
        }
        .padding(14).background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 14, style: .continuous)), id: id)
    }
}

/// The two halves of the 내 활동 tab.
enum MineSegment: CaseIterable {
    case mine, favorites
    var title: String {
        switch self {
        case .mine: "내 활동"
        case .favorites: "즐겨찾기"
        }
    }
}
