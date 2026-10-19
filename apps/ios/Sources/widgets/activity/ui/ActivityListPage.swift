import SwiftUI

/// 발견: 프로그램 카드 목록(contract "발견 카드 리뉴얼" 2026-10-19; iOS는 inset-grouped 섹션 관용구, AGENTS.md "플랫폼별 네이티브 디자인"). `마감 숨기기`가 켜지면 모집 중·모집 예정 활동이 있는
/// 프로그램만, 꺼지면 최근 3년 안에 활동이 있었던 프로그램까지 보인다. 카드를 펼치면 모집 중·모집 예정 활동이 항상, 지난 활동은 토글을 껐을 때만 보인다.
struct ActivityListPage: View {
    let state: ActivityViewModel
    @Binding var path: [String]
    @State private var showNotice = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        @Bindable var state = state
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                // The title line comes first (contract "탭 제목 통일"), then the search field and toggle right under it.
                PageTitle("활동 둘러보기") {
                    Button { showNotice = true } label: { Image(systemName: "bell").font(.title2).frame(width: 44, height: 44) }
                        .accessibilityLabel("알림")
                }
                LazyVStack(alignment: .leading, spacing: 10) {
                    DearbySearchField(prompt: "프로그램, 조직, 활동 이름으로 검색", text: $state.searchText, identifier: "activity-search")
                        .submitLabel(.search)
                    Toggle("마감 숨기기", isOn: $state.openOnly).font(.dearby(.subheadline)).tint(DearbyStyle.teal)
                        .accessibilityIdentifier("activity-open-only")
                    switch state.phase {
                    case .loading:
                        ProgressView("프로그램을 불러오는 중이에요").frame(maxWidth: .infinity).padding(.vertical, 40)
                    case .failed:
                        ContentUnavailableView {
                            Label("프로그램을 불러오지 못했어요", systemImage: "wifi.exclamationmark")
                        } description: {
                            Text("연결을 확인한 뒤 다시 시도해 주세요.")
                        } actions: {
                            Button("다시 시도") { state.reload() }.accessibilityIdentifier("activity-retry")
                        }
                    case .loaded where state.programs.isEmpty:
                        if let query = state.query, !query.isEmpty {
                            ContentUnavailableView("‘\(query)’에 맞는 프로그램이 없어요", systemImage: "magnifyingglass",
                                                   description: Text("다른 검색어로 찾아보세요."))
                        } else if state.openOnly {
                            ContentUnavailableView("지금 모집 중인 프로그램이 없어요", systemImage: "calendar",
                                                   description: Text("새 모집이 열리면 여기에서 볼 수 있어요. ‘마감 숨기기’를 끄면 최근 활동한 프로그램도 보여요."))
                        } else {
                            ContentUnavailableView("최근 2년 안에 활동한 프로그램이 없어요", systemImage: "calendar",
                                                   description: Text("활동이 수집되면 여기에서 볼 수 있어요."))
                        }
                    case .loaded:
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
                }.padding(.horizontal, 20).padding(.bottom, 20)
            }
        }
        .scrollDismissesKeyboard(.immediately)
        // Typing waits ~300ms before searching; the first load does not wait.
        .task(id: state.searchText) {
            if state.query != nil {
                try? await Task.sleep(for: .milliseconds(300))
                guard !Task.isCancelled else { return }
            }
            state.search(state.searchText)
        }
        .alert("알림", isPresented: $showNotice) { Button("확인") {} } message: { Text("새로운 알림이 없어요.") }
        .navigationDestination(for: String.self) { id in ActivityDetailView(state: state, activityID: id) }
        .scrollContentBackground(.hidden).background(Color(.systemGroupedBackground))
        .tint(DearbyStyle.teal).navigationTitle("")
        .navigationBarTitleDisplayMode(.inline).toolbar(.hidden, for: .navigationBar)
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
                     recruitmentLabel: activity.recruitmentLabel, dateLabel: activity.dateLabel, place: activity.place?.name ?? activity.location) {
            path.append(activity.id)
        }
    }
}
