import SwiftUI

/// 발견: 열린 활동이 있는 프로그램의 아코디언 목록(contract "발견 프로그램 묶음"). 머리글을 펼치면 그 프로그램의 열린 활동 카드가 보인다.
struct ActivityListPage: View {
    let state: ActivityViewModel
    @Binding var path: [String]
    @State private var showNotice = false
    var body: some View {
        @Bindable var state = state
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                HStack {
                    DearbyLogo(width: 96)
                    Spacer()
                    Button { showNotice = true } label: { Image(systemName: "bell").font(.title2).frame(width: 44, height: 44) }
                        .accessibilityLabel("알림")
                }.padding(.bottom, 8)
                Text("활동 둘러보기").font(.title2.bold())
                DearbySearchField(prompt: "프로그램, 조직, 활동 이름으로 검색", text: $state.searchText, identifier: "activity-search")
                    .submitLabel(.search)
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
                    } else {
                        ContentUnavailableView("지금 모집 중인 프로그램이 없어요", systemImage: "calendar",
                                               description: Text("새 모집이 열리면 여기에서 볼 수 있어요."))
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
            }.padding(20)
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
        .scrollContentBackground(.hidden).background(.white)
        .tint(DearbyStyle.teal).navigationTitle("")
        .navigationBarTitleDisplayMode(.inline).toolbar(.hidden, for: .navigationBar)
    }
    @ViewBuilder private func section(_ program: ProgramSummary) -> some View {
        let open = state.isExpanded(program.id)
        VStack(alignment: .leading, spacing: 10) {
            ProgramHeader(name: program.name, organization: program.organization, openCount: program.openActivityCount,
                          nearestDeadline: program.nearestRecruitmentEnd, expanded: open) { state.toggle(program.id) }
            if open {
                if let activities = state.openActivities[program.id] {
                    if activities.isEmpty {
                        Text("지금 열린 활동이 없어요.").font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).padding(.horizontal, 4)
                    } else {
                        // Grouped by 활동 종류 in contract order, 미분류 last; a single group needs no heading.
                        let groups = ActivityCategory.group(activities)
                        ForEach(groups, id: \.label) { group in
                            if groups.count > 1 {
                                Text("\(group.label) (\(group.activities.count))").font(.dearby(.subheadline).weight(.semibold))
                                    .foregroundStyle(DearbyStyle.quiet).padding(.horizontal, 4).padding(.top, 4).accessibilityAddTraits(.isHeader)
                            }
                            ForEach(group.activities) { activity in card(activity) }
                        }
                    }
                } else if state.failedPrograms.contains(program.id) {
                    Button("활동 다시 불러오기") { state.retryActivities(program.id) }.frame(maxWidth: .infinity, minHeight: 44)
                } else {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 44)
                }
            }
        }
    }
    private func card(_ activity: ActivityModel) -> some View {
        ActivityCard(activityID: activity.id, title: activity.title, organization: activity.organization,
            imageURL: activity.imageURL, summary: activity.summary, status: activity.statusLabel(at: state.loadedAt),
            date: activity.dateLabel.isEmpty ? "일정 미확인" : activity.dateLabel,
            applyURL: activity.applicationURL(at: state.loadedAt), recruitmentEnd: activity.recruitmentEnd) {
            path.append(activity.id)
        }
        .padding(.leading, 12)
        .environment(\.openURL, OpenURLAction { url in
            state.openedApplication(activity.id)
            return .systemAction(url)
        })
    }
}
