import SwiftUI

/// One program's activity cards (contract "발견 카드 리뉴얼"·"펼침"): open ones grouped by 활동 종류, then `모집 예정`, then `지난 활동` when
/// `showsPast`. Shared by the expanded discovery card and the program page. Each card carries the heart, bell and `+` (contract "즐겨찾기(하트)"·
/// "`+` 내 활동에 추가" 2026-10-25); a card opens the activity detail on `path`.
struct ProgramActivityGroups: View {
    let state: ActivityViewModel
    let programID: Int64
    @Binding var path: NavigationPath
    let showsPast: Bool
    var body: some View {
        if let activities = state.openActivities[programID] {
            let upcoming = state.upcomingActivities[programID] ?? []
            let past = showsPast ? (state.pastActivities[programID] ?? []) : []
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
            // `모집 예정` always, `지난 활동` only when asked (contract 2026-10-19 밤). Empty groups omitted.
            if !upcoming.isEmpty {
                heading("모집 예정")
                ForEach(upcoming) { activity in card(activity) }
            }
            if !past.isEmpty {
                heading("지난 활동")
                ForEach(past) { activity in card(activity) }
            }
        } else if state.failedPrograms.contains(programID) {
            Button("활동 다시 불러오기") { state.retryActivities(programID) }.frame(maxWidth: .infinity, minHeight: 44)
        } else {
            // Until both the open and the upcoming/past lists arrive (contract "내용 로딩은 스켈레톤" 2026-10-22).
            VStack(alignment: .leading, spacing: 8) {
                ForEach(0..<2, id: \.self) { _ in
                    ActivityCard(activityID: "", title: "불러오는 활동 이름", recruitment: .open, recruitmentText: "10월 20일 (화) ~ 11월 10일 (화)",
                                 scheduleText: "2026년 11월 24일 (화) ~ 2027년 2월 6일 (토)", place: "온라인", accessory: { EmptyView() }) {}
                }
            }.dearbySkeleton()
        }
    }
    private func heading(_ text: String) -> some View {
        Text(text).font(.dearby(.caption).weight(.semibold)).foregroundStyle(.secondary)
            .padding(.horizontal, 6).padding(.top, 6).accessibilityAddTraits(.isHeader)
    }
    private func card(_ activity: ActivityModel) -> some View {
        ActivityCard(activityID: activity.id, title: activity.title, recruitment: activity.recruitment(at: state.loadedAt),
                     recruitmentText: activity.recruitmentText(at: state.loadedAt), scheduleText: activity.scheduleText(at: state.loadedAt),
                     place: activity.place?.name ?? activity.location, fieldTag: activity.fieldTags.first, traitTags: activity.traitTags) {
            FavoriteControls(target: .activity(activity.id), mine: (state.appliedIDs.contains(activity.id), { state.addToMine(activity.id) }))
        } open: {
            path.append(activity.id)
        }
    }
}
