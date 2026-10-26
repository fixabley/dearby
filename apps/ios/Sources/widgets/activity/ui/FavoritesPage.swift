import SwiftUI

/// 즐겨찾기 (contract "즐겨찾기(하트)·새 모집 푸시 알림" 2026-10-25), the second half of the 내 활동 tab: the activities by recruitment date
/// (`FavoritesCalendarView`; the `목록` view went 2026-10-26), then `날짜 없는 즐겨찾기` — the programs (logo, name, next recruitment, `새 모집`
/// badge) and the activities without any date — as the discovery rows with their heart and bell (the level is spoken and shown by the bell symbol
/// and its title). Signed out: a sign-in prompt; nothing yet: `하트를 누르면 여기에 모여요`.
struct FavoritesPage: View {
    let state: ActivityViewModel
    @Binding var path: NavigationPath
    @Environment(FavoritesModel.self) private var favorites
    @Environment(SessionFavoritesModel.self) private var sessions
    var body: some View {
        Group {
            switch favorites.phase {
            case .signedOut:
                CenteredMessage(symbol: "heart", title: "즐겨찾기는 계정에 모여요", message: "로그인하면 하트를 누른 활동과 프로그램을 모아 볼 수 있어요.",
                                primary: MessageAction("로그인", identifier: "favorites-sign-in") { favorites.signInRequested = true })
            case .loading:
                ScrollView { DearbyRowsSkeleton(count: 5).padding(20) }.scrollDisabled(true)
            case .failed:
                CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "즐겨찾기를 불러오지 못했어요", message: "연결을 확인한 뒤 다시 시도해 주세요.",
                                primary: MessageAction("다시 시도") { Task { await favorites.load() } })
            case .loaded, .unavailable:
                if favorites.programs.isEmpty && favorites.activities.isEmpty {
                    CenteredMessage(symbol: "heart", title: "하트를 누르면 여기에 모여요", message: "발견에서 마음에 드는 활동과 프로그램에 하트를 눌러 보세요.")
                } else {
                    FavoritesCalendarView(items: favorites.activities, sessionDays: Set(sessions.items.map { SeoulDay($0.session.start) }),
                                          open: { path.append($0) }) { undated in
                        if !favorites.programs.isEmpty || !undated.isEmpty {
                            undatedSection(undated)
                        }
                    }
                    .refreshable { await favorites.load() }
                }
            }
        }
        // 찜한 세션 (contract "트랙·세션 시간표" 2026-10-26): with at least one, a row above the calendar opens 나만의 시간표.
        .safeAreaInset(edge: .top, spacing: 0) {
            if !sessions.items.isEmpty {
                Button { path.append(MySessionsRoute()) } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "heart.text.square").foregroundStyle(.pink)
                        Text("찜한 세션").font(.dearby(.body).weight(.semibold)).foregroundStyle(.primary)
                        Text("\(sessions.items.count)개").font(.dearby(.subheadline)).foregroundStyle(.secondary).monospacedDigit()
                        Spacer(minLength: 8)
                        Image(systemName: "chevron.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(.tertiary)
                    }
                    .padding(.horizontal, 16).frame(minHeight: 48)
                    .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 14, style: .continuous))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain).padding(.horizontal, 20).padding(.vertical, 8)
                .accessibilityLabel("찜한 세션 \(sessions.items.count)개").accessibilityHint("누르면 나만의 시간표를 보여요")
                .accessibilityIdentifier("favorite-sessions")
            }
        }
        .analyticsScreen("favorites")
    }
    /// 날짜 없는 즐겨찾기: the programs (never on the calendar) and the activities without any date.
    private func undatedSection(_ activities: [FavoriteActivity]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("날짜 없는 즐겨찾기").font(.dearby(.headline)).accessibilityAddTraits(.isHeader).padding(.top, 20)
            ForEach(favorites.programs) { programRow($0) }
            ForEach(activities) { activityRow($0) }
        }
        .padding(.horizontal, 20)
    }
    private func programRow(_ favorite: FavoriteProgram) -> some View {
        let program = favorite.program
        return ProgramHeader(name: program.name, organization: program.organization, category: program.category, logoURL: program.logoURL,
                             openCount: program.openActivityCount,
                             nearestDeadline: program.nearestRecruitmentEnd, upcomingCount: program.upcomingActivityCount,
                             nearestStart: program.nearestRecruitmentStart, latestActivity: program.latestActivity,
                             fieldTags: program.fieldTags, traitTags: program.traitTags, hasTimetable: program.hasTimetable, newRecruitment: favorite.newRecruitment, expanded: false) {
            FavoriteControls(target: .program(program.id), showsLevel: true)
        } toggle: {
            favorites.seen(program.id)
            path.append(ProgramRoute(program: program))
        }
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
    }
    private func activityRow(_ favorite: FavoriteActivity) -> some View {
        let activity = favorite.activity, now = Date(), recruitmentDate = activity.recruitmentDate(at: now)
        return ActivityCard(activityID: activity.id, title: activity.title, status: activity.status(at: now),
                            recruitmentText: recruitmentDate.text, recruitmentDDay: recruitmentDate.dday,
                            startDDay: activity.upcomingStartDDay(at: now), scheduleText: activity.scheduleText(at: now),
                            place: activity.place?.name ?? activity.location, fieldTag: activity.fieldTags.first, traitTags: activity.traitTags,
                            surface: Color(.secondarySystemGroupedBackground), hasTimetable: activity.hasTimetable) {
            FavoriteControls(target: .activity(activity.id), mine: (state.appliedIDs.contains(activity.id), {
                state.remember(activity)
                state.addToMine(activity.id, source: "favorites")
            }),
                             showsLevel: true)
        } open: {
            path.append(activity.id)
        }
    }
}

/// A program page pushed from 즐겨찾기; equal by program id.
struct ProgramRoute: Hashable {
    let program: ProgramSummary
    static func == (lhs: ProgramRoute, rhs: ProgramRoute) -> Bool { lhs.program.id == rhs.program.id }
    func hash(into hasher: inout Hasher) { hasher.combine(program.id) }
}

/// 프로그램 상세: the program's head with its heart and bell (contract "즐겨찾기(하트)": 프로그램 상세 머리), then its activities as on an expanded
/// discovery card, past ones included.
struct ProgramDetailPage: View {
    let state: ActivityViewModel
    let program: ProgramSummary
    @Binding var path: NavigationPath
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                ProgramHeader(name: program.name, organization: program.organization, category: program.category, logoURL: program.logoURL,
                              openCount: program.openActivityCount,
                              nearestDeadline: program.nearestRecruitmentEnd, upcomingCount: program.upcomingActivityCount,
                              nearestStart: program.nearestRecruitmentStart, latestActivity: program.latestActivity,
                              fieldTags: program.fieldTags, traitTags: program.traitTags, hasTimetable: program.hasTimetable, expanded: false,
                              accessory: { FavoriteControls(target: .program(program.id)) }, toggle: nil)
                    .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
                    .padding(.bottom, 8)
                ProgramActivityGroups(state: state, programID: program.id, path: $path, groups: Set(StatusGroup.allCases), source: "favorites")
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        // Loads again when a discovery search dropped the loaded lists.
        .task(id: state.activities(program.id, for: Set(StatusGroup.allCases)) == nil) { state.loadProgram(program.id) }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .navigationTitle(program.name).navigationBarTitleDisplayMode(.inline).analyticsScreen("program_detail")
    }
}
