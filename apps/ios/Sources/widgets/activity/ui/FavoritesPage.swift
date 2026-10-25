import SwiftUI

/// 즐겨찾기 (contract "즐겨찾기(하트)·새 모집 푸시 알림" 2026-10-25), the second half of the 내 활동 tab: `프로그램` (logo, name, next recruitment,
/// `새 모집` badge) then `활동` (the server's deadline order), each with its heart and bell (the level is spoken and shown by the bell symbol and
/// its title). Swiping a row removes it with a `되돌리기` notice. The top-right `목록 | 캘린더` switch (remembered on the device) shows the
/// activities by recruitment date instead (`FavoritesCalendarView`). Signed out: a sign-in prompt; nothing yet: `하트를 누르면 여기에 모여요`.
struct FavoritesPage: View {
    let state: ActivityViewModel
    @Binding var path: NavigationPath
    @Environment(FavoritesModel.self) private var favorites
    @AppStorage(FavoritesLayout.storageKey) private var layout = FavoritesLayout.list
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
                } else if layout == .calendar {
                    FavoritesCalendarView(items: favorites.activities) { path.append($0) }
                } else {
                    list
                }
            }
        }
        .toolbar {
            if favorites.phase != .signedOut {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Picker("보기", selection: $layout.reporting { if $0 == .calendar { AppAnalytics.event("menu_open", ["menu": "favorites_calendar"]) } }) {
                            Label("목록", systemImage: "list.bullet").tag(FavoritesLayout.list)
                            Label("캘린더", systemImage: "calendar").tag(FavoritesLayout.calendar)
                        }
                    } label: {
                        Image(systemName: layout == .calendar ? "calendar" : "list.bullet")
                    }
                    .accessibilityLabel("보기 전환").accessibilityValue(layout == .calendar ? "캘린더" : "목록")
                    .accessibilityIdentifier("favorites-layout")
                }
            }
        }
        .overlay(alignment: .bottom) { undoNotice }
        .onDisappear { favorites.commitPending() }
        .analyticsScreen(layout == .calendar ? "favorites_calendar" : "favorites")
    }
    private var list: some View {
        List {
            if !favorites.programs.isEmpty {
                heading("프로그램")
                ForEach(favorites.programs) { favorite in
                    row(programRow(favorite), target: .program(favorite.id))
                }
            }
            if !favorites.activities.isEmpty {
                heading("활동")
                ForEach(favorites.activities) { favorite in
                    row(activityRow(favorite), target: .activity(favorite.id))
                }
            }
        }
        .listStyle(.plain).environment(\.defaultMinListRowHeight, 0)
        .scrollContentBackground(.hidden)
        .refreshable { await favorites.load() }
    }
    private func programRow(_ favorite: FavoriteProgram) -> some View {
        let program = favorite.program
        return ProgramHeader(name: program.name, organization: program.organization, logoURL: program.logoURL, openCount: program.openActivityCount,
                             nearestDeadline: program.nearestRecruitmentEnd, upcomingCount: program.upcomingActivityCount,
                             nearestStart: program.nearestRecruitmentStart, latestActivity: program.latestActivity,
                             fieldTags: program.fieldTags, traitTags: program.traitTags, newRecruitment: favorite.newRecruitment, expanded: false) {
            FavoriteControls(target: .program(program.id), showsLevel: true)
        } toggle: {
            favorites.seen(program.id)
            path.append(ProgramRoute(program: program))
        }
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
    }
    private func activityRow(_ favorite: FavoriteActivity) -> some View {
        let activity = favorite.activity, now = Date()
        return ActivityCard(activityID: activity.id, title: activity.title, recruitment: activity.recruitment(at: now),
                            recruitmentText: activity.recruitmentText(at: now), scheduleText: activity.scheduleText(at: now),
                            place: activity.place?.name ?? activity.location, fieldTag: activity.fieldTags.first, traitTags: activity.traitTags,
                            surface: Color(.secondarySystemGroupedBackground)) {
            FavoriteControls(target: .activity(activity.id), mine: (state.appliedIDs.contains(activity.id), { state.addToMine(activity.id, source: "favorites") }),
                             showsLevel: true)
        } open: {
            path.append(activity.id)
        }
    }
    private func heading(_ text: String) -> some View {
        Text(text).font(.dearby(.headline)).accessibilityAddTraits(.isHeader)
            .frame(maxWidth: .infinity, alignment: .leading)
            .listRowBackground(Color.clear).listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: 16, leading: 20, bottom: 4, trailing: 20))
    }
    /// One card row: no list background or separator; a trailing swipe removes it (with the undo notice).
    private func row(_ content: some View, target: FavoriteTarget) -> some View {
        content
            .listRowBackground(Color.clear).listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: 6, leading: 20, bottom: 6, trailing: 20))
            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                Button("해제", systemImage: "heart.slash", role: .destructive) { withAnimation { favorites.removeWithUndo(target) } }
            }
    }
    @ViewBuilder private var undoNotice: some View {
        if let pending = favorites.pending {
            HStack(spacing: 12) {
                Text("즐겨찾기에서 뺐어요").font(.dearby(.subheadline)).foregroundStyle(.primary)
                Spacer(minLength: 8)
                Button("되돌리기") { withAnimation { favorites.undo() } }.font(.dearby(.subheadline).weight(.semibold))
            }
            .padding(.horizontal, 16).frame(minHeight: 48)
            .background(.regularMaterial, in: .capsule)
            .padding(.horizontal, 16).padding(.bottom, 12)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .task(id: pending.target) {
                AccessibilityNotification.Announcement("즐겨찾기에서 뺐어요").post()
                try? await Task.sleep(for: .seconds(4))
                if !Task.isCancelled { withAnimation { favorites.commitPending() } }
            }
        }
    }
}

/// The favorites screen's `목록 | 캘린더` choice, kept on the device (a new key, 2026-10-25).
enum FavoritesLayout: String {
    case list, calendar
    static let storageKey = "dearby.favorites.layout"
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
                ProgramHeader(name: program.name, organization: program.organization, logoURL: program.logoURL, openCount: program.openActivityCount,
                              nearestDeadline: program.nearestRecruitmentEnd, upcomingCount: program.upcomingActivityCount,
                              nearestStart: program.nearestRecruitmentStart, latestActivity: program.latestActivity,
                              fieldTags: program.fieldTags, traitTags: program.traitTags, expanded: false,
                              accessory: { FavoriteControls(target: .program(program.id)) }, toggle: nil)
                    .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
                    .padding(.bottom, 8)
                ProgramActivityGroups(state: state, programID: program.id, path: $path, showsPast: true, source: "favorites")
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
        }
        // Loads again when a discovery search dropped the loaded lists.
        .task(id: state.openActivities[program.id] == nil) { state.loadProgram(program.id) }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .navigationTitle(program.name).navigationBarTitleDisplayMode(.inline).analyticsScreen("program_detail")
    }
}
