import SwiftUI

/// 발견: 프로그램 카드 목록(contract "발견 카드 리뉴얼" 2026-10-19; iOS는 inset-grouped 섹션 관용구, AGENTS.md "플랫폼별 네이티브 디자인"). `마감 숨기기`가 켜지면 모집 중·모집 예정 활동이 있는
/// 프로그램만, 꺼지면 최근 3년 안에 활동이 있었던 프로그램까지 보인다. 카드를 펼치면 모집 중·모집 예정 활동이 항상, 지난 활동은 토글을 껐을 때만 보인다.
/// 상단은 시스템 내비게이션 바(contract "iOS 상단 바는 시스템 내비게이션 바" 2026-10-20): inline 제목 `활동 둘러보기`, 우상단 설정·알림, 검색은 `.searchable`의 내비게이션 바 서랍(맨 위에서는 펼침, 스크롤하면 접힘)
/// (아래로 스크롤하면 시스템이 접고 위로 당기면 다시 보인다). `마감 숨기기`는 목록의 첫 행이고, 풀투 리프레시·맨 위로 버튼은 그대로다.
struct ActivityListPage: View {
    let state: ActivityViewModel
    @Binding var path: NavigationPath
    /// More than one screen down: show `맨 위로`.
    @State private var farDown = false
    @State private var position = ScrollPosition()
    /// The scroll view's visible height: empty and error states centre in it (contract "비스크롤 화면 배치").
    @State private var viewport: CGFloat = 0
    /// Scroll offset at (or above) the top. The search drawer is `.always` here so it is open on first appearance and after returning to the top
    /// (contract "발견 검색·접힘" 2026-10-20: `.automatic` alone starts collapsed with an inline title); once the user scrolls it becomes `.automatic`
    /// so the system folds it away on the way down and brings it back on the way up.
    @State private var atTop = true
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        @Bindable var state = state
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 10) {
                if state.phase != .loading { openOnlyRow }
                switch state.phase {
                case .loading:
                    ProgressView("프로그램을 불러오는 중이에요").frame(maxWidth: .infinity).padding(.vertical, 40)
                case .failed:
                    CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "프로그램을 불러오지 못했어요",
                                    message: "연결을 확인한 뒤 다시 시도해 주세요.", embedded: true,
                                    primary: MessageAction("다시 시도", identifier: "activity-retry") { state.reload() })
                        .frame(minHeight: viewport)
                case .loaded where state.programs.isEmpty:
                    if let query = state.query, !query.isEmpty {
                        CenteredMessage(symbol: "magnifyingglass", title: "‘\(query)’에 맞는 프로그램이 없어요", message: "다른 검색어로 찾아보세요.", embedded: true)
                            .frame(minHeight: viewport)
                    } else if state.openOnly {
                        CenteredMessage(symbol: "calendar", title: "지금 모집 중인 프로그램이 없어요",
                                        message: "새 모집이 열리면 여기에서 볼 수 있어요. ‘마감 숨기기’를 끄면 최근 활동한 프로그램도 보여요.", embedded: true,
                                        primary: MessageAction("마감 숨기기 끄기", identifier: "activity-show-closed") { state.openOnly = false })
                            .frame(minHeight: viewport)
                    } else {
                        CenteredMessage(symbol: "calendar", title: "최근 활동한 프로그램이 없어요", message: "활동이 수집되면 여기에서 볼 수 있어요.", embedded: true)
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
        .onScrollGeometryChange(for: Bool.self) { $0.contentOffset.y + $0.contentInsets.top <= 0 } action: { _, top in atTop = top }
        .searchable(text: $state.searchText, placement: .navigationBarDrawer(displayMode: atTop ? .always : .automatic), prompt: "프로그램·활동·태그 검색")
        // Typing waits ~300ms before searching; the first load does not wait.
        .task(id: state.searchText) {
            if state.query != nil {
                try? await Task.sleep(for: .milliseconds(300))
                guard !Task.isCancelled else { return }
            }
            state.search(state.searchText)
        }
        .navigationDestination(for: String.self) { id in ActivityDetailView(state: state, activityID: id) }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .tint(DearbyStyle.teal)
        .navigationTitle("활동 둘러보기").navigationBarTitleDisplayMode(.inline)
        .toolbar { TabRootToolbar() }
    }
    /// `마감 숨기기` as the first list row: the whole row toggles (one control; the switch is the row's indicator), label beside the switch.
    private var openOnlyRow: some View {
        Button { state.openOnly.toggle() } label: {
            HStack {
                Text("마감 숨기기").font(.dearby(.body)).foregroundStyle(.primary)
                Spacer(minLength: 12)
                Toggle("마감 숨기기", isOn: .constant(state.openOnly)).labelsHidden().tint(DearbyStyle.teal).allowsHitTesting(false)
            }
            .padding(.horizontal, 16).frame(minHeight: 44)
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 14, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore).accessibilityLabel("마감 숨기기").accessibilityValue(state.openOnly ? "켬" : "끔")
        .accessibilityAddTraits(.isToggle).accessibilityIdentifier("activity-open-only")
    }
    /// `맨 위로`: no animation (contract).
    private func scrollToTop() {
        position.scrollTo(edge: .top)
    }
    /// One program card (contract "발견 카드 리뉴얼", iOS inset-grouped idiom): the header and, when expanded, the activity groups inside the same
    /// rounded section. Height follows the system spring; the content fades in from the top; reduced motion switches at once.
    @ViewBuilder private func section(_ program: ProgramSummary) -> some View {
        let open = state.isExpanded(program.id)
        VStack(alignment: .leading, spacing: 0) {
            ProgramHeader(name: program.name, organization: program.organization, logoURL: program.logoURL, openCount: program.openActivityCount,
                          nearestDeadline: program.nearestRecruitmentEnd, upcomingCount: program.upcomingActivityCount,
                          nearestStart: program.nearestRecruitmentStart, latestActivity: program.latestActivity, tags: program.tags, expanded: open) {
                withAnimation(reduceMotion ? nil : .spring(response: 0.35, dampingFraction: 0.85)) { state.toggle(program.id) }
            }
            if open {
                VStack(alignment: .leading, spacing: 8) { activities(program) }
                    .padding(EdgeInsets(top: 0, leading: 10, bottom: 12, trailing: 10))
                    .transition(reduceMotion ? .identity : .opacity.combined(with: .move(edge: .top)))
            }
        }
        // Inset-grouped look: a secondary grouped surface on the grouped page background, continuous corners, no stroke.
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
        .clipShape(.rect(cornerRadius: 20, style: .continuous))
    }
    @ViewBuilder private func activities(_ program: ProgramSummary) -> some View {
        if let activities = state.openActivities[program.id] {
            let upcoming = state.upcomingActivities[program.id] ?? []
            let past = state.openOnly ? [] : (state.pastActivities[program.id] ?? [])
            if activities.isEmpty && upcoming.isEmpty && past.isEmpty {
                Text("지금 모집 중인 활동이 없어요").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).padding(.horizontal, 6)
            } else if !activities.isEmpty {
                // Grouped by 활동 종류 in contract order, 미분류 last; a single group needs no heading.
                let groups = ActivityCategory.group(activities)
                ForEach(groups, id: \.label) { group in
                    if groups.count > 1 { heading("\(group.label) (\(group.activities.count))") }
                    ForEach(group.activities) { activity in card(activity) }
                }
            }
            // `모집 예정` always, `지난 활동` only while 마감 숨기기 is off (contract 2026-10-19 밤). Empty groups omitted.
            if !upcoming.isEmpty {
                heading("모집 예정")
                ForEach(upcoming) { activity in card(activity) }
            }
            if !past.isEmpty {
                heading("지난 활동")
                ForEach(past) { activity in card(activity) }
            }
        } else if state.failedPrograms.contains(program.id) {
            Button("활동 다시 불러오기") { state.retryActivities(program.id) }.frame(maxWidth: .infinity, minHeight: 44)
        } else {
            ProgressView().frame(maxWidth: .infinity, minHeight: 44)
        }
    }
    private func heading(_ text: String) -> some View {
        Text(text).font(.dearby(.caption).weight(.semibold)).foregroundStyle(.secondary)
            .padding(.horizontal, 6).padding(.top, 6).accessibilityAddTraits(.isHeader)
    }
    private func card(_ activity: ActivityModel) -> some View {
        ActivityCard(activityID: activity.id, title: activity.title, recruitment: activity.recruitment(at: state.loadedAt),
                     recruitmentText: activity.recruitmentText(at: state.loadedAt), scheduleText: activity.scheduleText(at: state.loadedAt),
                     place: activity.place?.name ?? activity.location) {
            path.append(activity.id)
        }
    }
}
