import SwiftUI

/// 발견: 프로그램 카드 목록(contract "발견 카드 리뉴얼" 2026-10-19; iOS는 inset-grouped 섹션 관용구, AGENTS.md "플랫폼별 네이티브 디자인"). 첫 행의 `필터`
/// 버튼과 적용된 필터 칩(contract "필터 시트" 2026-10-26: 시트에서 `활동 상태` 묶음(`지원 가능` 기본·`진행·예정`·`지난 활동`)과 프로그램 쪽 `종류`를 고른다)으로
/// 거른 활동이 있는 프로그램만 보이고, 카드를 펼치면 그 상태·종류의 활동만 보인다.
/// 상단은 시스템 내비게이션 바(contract "iOS 상단 바는 시스템 내비게이션 바" 2026-10-20): inline 제목 `활동 둘러보기`, 가운데 `프로그램 | 이벤트`, 우상단 설정·알림, 검색은
/// `.searchable`의 내비게이션 바 서랍(맨 위에서는 펼침, 아래로 스크롤하면 시스템이 접고 위로 당기면 다시 보인다). 풀투 리프레시·맨 위로 버튼은 그대로다.
/// 맨 위, 맞춤 대기열 행 위에 `오늘 내 행사` 카드(contract "오늘 내 행사 카드" 2026-10-26, `TodayEvent`·`TodayEventCard`)가 로그인 상태에서 해당 활동이 있을 때만 보인다.
struct ActivityListPage: View {
    let state: ActivityViewModel
    /// `오늘의 맞춤 대기열` (user 2026-10-25 "추천은 없애고 맞춤대기열 진입만 남겨줘"): the one row above `활동 상태`, hidden only while
    /// searching (the queue screen shows its own empty and retry states); the app opens the queue (the funnel first when it was never
    /// finished). Its `설정` reopens the funnel. Signed out the row stays and the app opens sign-in instead (contract "비로그인에서 만들기·추천을 막음").
    let queueSignedIn: Bool
    /// Signed in but the consent is missing: the row asks for the consent instead of sign-in.
    var queueNeedsConsent = false
    let openQueue: () -> Void
    let editRecommendations: () -> Void
    /// `명함 QR` on the 오늘 내 행사 card (contract "오늘 내 행사 카드"): 내 명함's QR 보이기 with this activity as the chip.
    var showQR: (String) -> Void = { _ in }
    @Binding var path: NavigationPath
    /// 세션 찜 for the 오늘 내 행사 card's next session; the card shows only signed in (`queueSignedIn`), when the list is loaded and not searching.
    @Environment(SessionFavoritesModel.self) private var sessionFavorites
    /// The 오늘 내 행사 card's block timetable, the same sheet as the activity detail and 진행 단계.
    @State private var timetable: TimetableRoute?
    /// More than one screen down: show `맨 위로`.
    @State private var farDown = false
    @State private var position = ScrollPosition()
    /// The scroll view's visible height: empty and error states centre in it (contract "비스크롤 화면 배치").
    @State private var viewport: CGFloat = 0
    /// The user has scrolled the list. The search drawer is `.always` until then so it is open on first appearance (contract "발견 검색·접힘"
    /// 2026-10-20: `.automatic` alone starts collapsed with an inline title); after that it is `.automatic` for good, so the system folds it away on
    /// the way down and brings it back on the way up. One way only: deriving the mode from the scroll offset looped (the drawer changes the inset
    /// the offset is measured against), which hung the app on expand/collapse and in the background.
    @State private var scrolled = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    #if DEBUG
    @State private var screenshotShown = false
    #endif
    var body: some View {
        @Bindable var state = state
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 10) {
                if state.phase != .loading, (state.query ?? "").isEmpty {
                    // One child of the lazy stack, so a missing card leaves no gap; the minute tick moves `지금 ·` and the next session along.
                    TimelineView(.everyMinute) { context in
                        VStack(alignment: .leading, spacing: 10) {
                            if queueSignedIn, let event = TodayEvent.pick(state.appliedProgress(now: context.date), favorites: sessionFavorites.items, now: context.date) {
                                TodayEventCard(event: event, now: context.date, open: { open(event, now: context.date) }, showQR: { showQR(event) })
                            }
                            queueEntry
                        }
                    }
                }
                // Always on screen: applying reloads the list (`.loading`) and the bar must not jump away under the sheet.
                DiscoveryFilterBar(kind: state.kind, groups: state.statusGroups, categories: state.categories, apply: apply)
                switch state.phase {
                case .loading:
                    programsSkeleton
                case .failed:
                    CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "프로그램을 불러오지 못했어요",
                                    message: "연결을 확인한 뒤 다시 시도해 주세요.", embedded: true,
                                    primary: MessageAction("다시 시도", identifier: "activity-retry") { state.reload() })
                        .frame(minHeight: viewport)
                case .loaded where state.programs.isEmpty:
                    if let query = state.query, !query.isEmpty {
                        CenteredMessage(symbol: "magnifyingglass", title: "‘\(query)’에 맞는 프로그램이 없어요", message: "다른 검색어로 찾아보세요.", embedded: true)
                            .frame(minHeight: viewport)
                    } else if !state.categories.isEmpty {
                        // 종류 필터 (contract "종류 필터" 2026-10-26): the way out is clearing the 종류.
                        CenteredMessage(symbol: "calendar", title: "고른 조건의 \(state.kind == .event ? "이벤트가" : "프로그램이") 없어요",
                                        message: "다른 종류를 고르거나 종류 필터를 지워 보세요.", embedded: true,
                                        primary: MessageAction("종류 필터 지우기", identifier: "activity-clear-categories") { apply(state.statusGroups, []) })
                            .frame(minHeight: viewport)
                    } else if !state.statusGroups.contains(.past) {
                        CenteredMessage(symbol: "calendar", title: emptyTitle,
                                        message: "새 모집이 열리면 여기에서 볼 수 있어요. ‘지난 활동’도 고르면 최근 활동한 프로그램도 보여요.", embedded: true,
                                        primary: MessageAction("지난 활동도 보기", identifier: "activity-show-closed") {
                                            apply(state.statusGroups.union([.past]), state.categories)
                                        })
                            .frame(minHeight: viewport)
                    } else {
                        CenteredMessage(symbol: "calendar", title: state.kind == .event ? "최근 열린 이벤트가 없어요" : "최근 활동한 프로그램이 없어요", message: "활동이 수집되면 여기에서 볼 수 있어요.", embedded: true)
                            .frame(minHeight: viewport)
                    }
                case .loaded:
                    if state.refreshFailed {
                        // Pull to refresh failed: the list stays, this line passes (contract "풀투 리프레시" failure, Android parity).
                        Label("새로 고치지 못했어요", systemImage: "exclamationmark.triangle")
                            .font(.dearby(.footnote)).foregroundStyle(.secondary).frame(maxWidth: .infinity).padding(.vertical, 6)
                            .accessibilityIdentifier("activity-refresh-failed").transition(.opacity)
                            .task {
                                AccessibilityNotification.Announcement("새로 고치지 못했어요").post()
                                try? await Task.sleep(for: .seconds(3))
                                if !Task.isCancelled { state.clearRefreshFailure() }
                            }
                    }
                    ForEach(Array(state.programs.enumerated()), id: \.element.id) { index, program in
                        section(program)
                            // Near the end of the list: fetch the next page.
                            .onAppear { if index >= state.programs.count - 3 { state.loadMore() } }
                    }
                    if state.loadingMore {
                        ProgressView().frame(maxWidth: .infinity, minHeight: 44)
                    } else if state.moreFailed {
                        Button("프로그램 더 불러오기") { state.loadMore() }.frame(maxWidth: .infinity, minHeight: 44)
                            .accessibilityIdentifier("activity-more-retry")
                    }
                }
            }.padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 20)
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: state.refreshFailed)
        }
        .scrollPosition($position)
        .overlay(alignment: .bottomTrailing) {
            if farDown {
                // 시스템 원형 버튼(사용자 2026-10-19 밤 "iOS는 기본 플로팅 버튼 UI 활용"): 직접 그린 원·그림자 대신 bordered prominent + circle.
                Button(action: scrollToTop) { Image(systemName: "arrow.up").font(.body.weight(.semibold)) }
                    .buttonStyle(.borderedProminent).buttonBorderShape(.circle).controlSize(.large).tint(DearbyStyle.tealFill)
                    .accessibilityLabel("맨 위로").accessibilityIdentifier("activity-scroll-top")
                    .padding(.trailing, 20).padding(.bottom, 16)
                    .transition(.opacity)
            }
        }
        .refreshable { await state.refresh() }
        .scrollDismissesKeyboard(.interactively)
        .onScrollGeometryChange(for: Bool.self) { $0.contentOffset.y + $0.contentInsets.top > $0.containerSize.height } action: { _, far in
            withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.2)) { farDown = far }
        }
        .onScrollGeometryChange(for: CGFloat.self) { $0.containerSize.height - $0.contentInsets.top - $0.contentInsets.bottom } action: { _, height in
            viewport = max(height - 80, 0)
        }
        .onScrollPhaseChange { _, phase in if phase == .interacting, !scrolled { scrolled = true } }
        .searchable(text: $state.searchText, placement: .navigationBarDrawer(displayMode: scrolled ? .automatic : .always), prompt: "프로그램·활동·태그 검색")
        // Typing waits ~300ms before searching; the first load does not wait.
        .task(id: state.searchText) {
            if state.query != nil {
                try? await Task.sleep(for: .milliseconds(300))
                guard !Task.isCancelled else { return }
            }
            state.search(state.searchText)
        }
        .timetableSheet($timetable, state: state)
        .navigationDestination(for: String.self) { id in ActivityDetailView(state: state, activityID: id) }
        // An `/activities/<id>` link (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): push its detail once this tab is on screen.
        .onChange(of: state.showInDiscovery, initial: true) { _, id in
            guard let id else { return }
            state.showInDiscovery = nil
            path.append(id)
        }
        #if DEBUG
        .task { await showScreenshotScene() }
        #endif
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .tint(DearbyStyle.teal)
        .navigationTitle("활동 둘러보기").navigationBarTitleDisplayMode(.inline).analyticsScreen("discovery")
        .toolbar {
            // 발견 `프로그램 | 이벤트` (user 2026-10-26) in the bar's title place, as 내 활동's segment.
            ToolbarItem(placement: .principal) {
                Picker("발견 구역", selection: $state.kind.reporting { AppAnalytics.event("discovery_mode", ["mode": $0.rawValue]) }) {
                    ForEach(DiscoveryKind.allCases, id: \.self) { Text($0.title).tag($0) }
                }.pickerStyle(.segmented).accessibilityIdentifier("discovery-kind")
            }
            TabRootToolbar()
        }
    }
    /// The 오늘 내 행사 card pressed (사용 통계 `today_card_tap`): today with a timetable on its block, that block's sheet on `찜한 세션` when any
    /// session of it is favorited (else `전체`); otherwise the activity's 진행 단계 on the 내 활동 tab (`showInMine`, as `내 활동으로`).
    private func open(_ event: TodayEvent, now: Date) {
        let block = event.block
        if event.timing == .today, block.hasTimetable {
            report(event, "timetable")
            timetable = TimetableRoute(activityID: event.activity.id, activityTitle: event.activity.title, scheduleID: block.id,
                                       scheduleTitle: event.blockTitle, date: block.text(now: now) ?? "",
                                       favoritesFirst: sessionFavorites.items.contains { $0.activityID == event.activity.id && $0.scheduleID == block.id })
        } else {
            report(event, "progress")
            state.showInMine = event.activity.id
        }
    }
    private func showQR(_ event: TodayEvent) {
        report(event, "qr")
        showQR(event.activity.id)
    }
    private func report(_ event: TodayEvent, _ target: String) {
        AppAnalytics.event("today_card_tap", AppAnalytics.activity(event.activity.id, event.activity).merging(["target": target]) { $1 })
    }
    private var queueEntry: some View {
        DiscoveryQueueEntry(signedIn: queueSignedIn, needsConsent: queueNeedsConsent, action: openQueue, settings: editRecommendations).padding(.leading, 16).padding(.trailing, 6)
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 14, style: .continuous))
    }
    /// The 필터 sheet's `적용`, a chip's × or an empty state's button: applies both filters in one reload and reports the ones that changed
    /// (사용 통계 `discovery_status_filter` with the 묶음 ids, `discovery_category_filter` with the 종류 codes, empty for 전체).
    private func apply(_ groups: Set<StatusGroup>, _ categories: Set<ActivityCategory>) {
        let changed = state.applyFilters(groups: groups, categories: categories)
        if changed.groups {
            AppAnalytics.event("discovery_status_filter", ["groups": StatusGroup.allCases.filter(state.statusGroups.contains).map(\.rawValue).joined(separator: ",")])
        }
        if changed.categories {
            AppAnalytics.event("discovery_category_filter", ["categories": state.categoryList.map { $0.rawValue.lowercased() }.joined(separator: ",")])
        }
    }
    /// The empty list's title without 지난 활동: the old wording for `지원 가능` alone, else the chosen states.
    private var emptyTitle: String {
        let noun = state.kind == .event ? "이벤트가" : "프로그램이"
        guard state.statusGroups == [.available] else { return "고른 조건의 \(noun) 없어요" }
        return state.kind == .event ? "지금 열리는 이벤트가 없어요" : "지금 모집 중인 프로그램이 없어요"
    }
    #if DEBUG
    /// Store screenshots (`ScreenshotScene`): the scenes on the discovery stack, once.
    private func showScreenshotScene() async {
        guard !screenshotShown else { return }
        screenshotShown = true
        switch ScreenshotScene.current {
        case .discoverExpanded: _ = await state.screenshotActivity()
        case .detail: if let id = await state.screenshotActivity() { path.append(id) }
        case .settings: path.append(SettingsRoute())
        default: break
        }
    }
    #endif
    /// `맨 위로`: no animation (contract).
    private func scrollToTop() {
        position.scrollTo(edge: .top)
    }
    /// One program card (contract "발견 카드 리뉴얼", iOS inset-grouped idiom): the header and, when expanded, the activity groups inside the same
    /// rounded section. Height follows the system spring; the content fades in from the top; reduced motion switches at once.
    @ViewBuilder private func section(_ program: ProgramSummary) -> some View {
        let open = state.isExpanded(program.id)
        VStack(alignment: .leading, spacing: 0) {
            ProgramHeader(name: program.name, organization: program.organization, category: program.category, logoURL: program.logoURL,
                          openCount: program.openActivityCount,
                          nearestDeadline: program.nearestRecruitmentEnd, upcomingCount: program.upcomingActivityCount,
                          nearestStart: program.nearestRecruitmentStart, latestActivity: program.latestActivity, statusCounts: program.statusCounts,
                          fieldTags: program.fieldTags, traitTags: program.traitTags, hasTimetable: program.hasTimetable,
                          matchedCount: program.matchedActivityIDs.count, eventDates: state.kind == .event, expanded: open) {
                FavoriteControls(target: .program(program.id))
            } toggle: {
                withAnimation(reduceMotion ? nil : .spring(response: 0.35, dampingFraction: 0.85)) { state.toggle(program.id) }
            }
            if open {
                VStack(alignment: .leading, spacing: 8) {
                    ProgramActivityGroups(state: state, programID: program.id, path: $path, groups: state.statusGroups, categories: state.categories)
                }
                    .padding(EdgeInsets(top: 0, leading: 10, bottom: 12, trailing: 10))
                    .transition(reduceMotion ? .identity : .opacity.combined(with: .move(edge: .top)))
            }
        }
        // Inset-grouped look: a secondary grouped surface on the grouped page background, continuous corners, no stroke.
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
        .clipShape(.rect(cornerRadius: 20, style: .continuous))
    }
    /// The first load (contract "내용 로딩은 스켈레톤" 2026-10-22): collapsed program cards with sample values.
    private var programsSkeleton: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(0..<4, id: \.self) { _ in
                ProgramHeader(name: "불러오는 프로그램", organization: "주최 기관 이름", category: .bootcamp, openCount: 1, nearestDeadline: nil, expanded: false) { EmptyView() } toggle: {}
                    .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
            }
        }.dearbySkeleton()
    }
}
