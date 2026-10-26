import SwiftUI

/// One program's activity cards (contract "발견 카드 리뉴얼"·"펼침"·"활동 상태"): for the chosen `groups`, 모집 중 grouped by 활동 종류, then
/// `모집 예정` (both 지원 가능), `진행·예정` and `지난 활동`. Shared by the expanded discovery card and the program page (every group). Each card carries the heart, bell and `+` (contract "즐겨찾기(하트)"·
/// "`+` 내 활동에 추가" 2026-10-25); a card opens the activity detail on `path`.
struct ProgramActivityGroups: View {
    let state: ActivityViewModel
    let programID: Int64
    @Binding var path: NavigationPath
    let groups: Set<StatusGroup>
    /// The 종류 the activities are read with (discovery's 종류 필터); empty on a program page (every 종류).
    var categories: Set<ActivityCategory> = []
    /// 사용 통계 `myactivity_add` source: `discovery`, or `favorites` on a favorite program's page.
    var source = "discovery"
    var body: some View {
        if let loaded = state.activities(programID, for: groups, categories: categories) {
            let available = groups.contains(.available)
            let activities = available ? loaded.recruiting : [], upcoming = available ? loaded.upcoming : []
            let inProgress = groups.contains(.inProgress) ? loaded.inProgress : [], past = groups.contains(.past) ? loaded.past : []
            if activities.isEmpty && upcoming.isEmpty && inProgress.isEmpty && past.isEmpty {
                Text(groups == [.available] ? "지금 모집 중인 활동이 없어요" : "고른 상태의 활동이 없어요").font(.dearby(.subheadline))
                    .foregroundStyle(DearbyStyle.quiet).padding(.horizontal, 6)
            } else if !activities.isEmpty {
                // Grouped by 활동 종류 in contract order, 미분류 last; a single group needs no heading.
                let groups = ActivityCategory.group(activities)
                ForEach(groups, id: \.label) { group in
                    if groups.count > 1 { heading("\(group.label) (\(group.activities.count))") }
                    ForEach(group.activities) { activity in card(activity) }
                }
            }
            // Empty groups omitted.
            group("모집 예정", upcoming)
            group(StatusGroup.inProgress.title, inProgress)
            group("지난 활동", past)
        } else if state.failedPrograms.contains(programID) {
            Button("활동 다시 불러오기") { state.retryActivities(programID, groups: groups, categories: categories) }.frame(maxWidth: .infinity, minHeight: 44)
        } else {
            // Until the activities arrive (contract "내용 로딩은 스켈레톤" 2026-10-22).
            VStack(alignment: .leading, spacing: 8) {
                ForEach(0..<2, id: \.self) { _ in
                    ActivityCard(activityID: "", title: "불러오는 활동 이름", status: .recruiting, recruitmentText: "10.20 – 11.10",
                                 scheduleText: "2026년 11월 24일 (화) ~ 2027년 2월 6일 (토)", place: "온라인", accessory: { EmptyView() }) {}
                }
            }.dearbySkeleton()
        }
    }
    @ViewBuilder private func group(_ title: String, _ activities: [ActivityModel]) -> some View {
        if !activities.isEmpty {
            heading(title)
            ForEach(activities) { activity in card(activity) }
        }
    }
    private func heading(_ text: String) -> some View {
        Text(text).font(.dearby(.caption).weight(.semibold)).foregroundStyle(.secondary)
            .padding(.horizontal, 6).padding(.top, 6).accessibilityAddTraits(.isHeader)
    }
    private func card(_ activity: ActivityModel) -> some View {
        let recruitmentDate = activity.recruitmentDate(at: state.loadedAt)
        return ActivityCard(activityID: activity.id, title: activity.title, status: activity.status(at: state.loadedAt),
                     recruitmentText: recruitmentDate.text, recruitmentDDay: recruitmentDate.dday,
                     startDDay: activity.upcomingStartDDay(at: state.loadedAt), scheduleText: activity.scheduleText(at: state.loadedAt),
                     place: activity.place?.name ?? activity.location, fieldTag: activity.fieldTags.first, traitTags: activity.traitTags,
                     hasTimetable: activity.hasTimetable) {
            FavoriteControls(target: .activity(activity.id), mine: (state.appliedIDs.contains(activity.id), { state.addToMine(activity.id, source: source) }))
        } open: {
            path.append(activity.id)
        }
    }
}
