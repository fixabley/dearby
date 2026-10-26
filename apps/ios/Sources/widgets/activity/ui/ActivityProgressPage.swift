import SwiftUI

/// Route to an applied activity's timeline; the activity detail keeps the plain String route.
struct ActivityProgressRoute: Hashable {
    let activityID: String
}

/// 진행 단계 (contract "내 활동 진행 트랙 — 2026-10-18", 정리 "진행 단계 화면 정리" 2026-10-26, "선발 과정·활동 일정 분리" 2026-10-26): an
/// inset-grouped `List` with system separators. `활동 정보` is the discovery `ActivityCard` (opens the detail) with the program and organization
/// under it. The stages split into `선발 과정` and `활동 일정` (an empty one is left out), one row each (icon, name, date · 지남/진행 중/예정, and
/// on the right the marked status of a 선발 과정 stage only); the current state (`D-3 · 면접`/`지남`/`탈락`) sits on the first header, and once
/// every selection stage is passed the `활동 일정` header carries `참여 중` instead. Pressing a row opens its menu (the schedule's statuses,
/// contract "진행 단계 상태" 2026-10-22, `캘린더에 추가`, `겹치는 시간 확인`); a block with a 트랙·세션 시간표 shows the `tablecells` glyph and
/// its menu starts with `시간표 보기` (user 2026-10-26), opening the same sheet as the activity detail. `내 활동에서 빼기` is the last row.
/// A FAILED kind ends the track.
struct ActivityProgressPage: View {
    let state: ActivityViewModel
    let activityID: String
    /// The card opens the activity detail on the 내 활동 stack.
    let openDetail: () -> Void
    @State private var calendarStage: ActivityStage?
    @State private var calendarDraft: CalendarEventDraft?
    @State private var timetable: TimetableRoute?
    @State private var removing: String?
    @Environment(\.dismiss) private var dismiss
    private var activity: ActivityModel? { state.activity(activityID) }
    var body: some View {
        Group {
            if let activity, let progress = state.progress(activity, now: Date()) {
                List {
                    Section {
                        card(activity).listRowInsets(EdgeInsets())
                    } header: {
                        Text("활동 정보")
                    } footer: {
                        let line = [activity.program, activity.organization].compactMap { $0 }.joined(separator: " · ")
                        if !line.isEmpty { Text(line) }
                    }
                    let selection = progress.stages.filter { !$0.inActivitySection }, activities = progress.stages.filter(\.inActivitySection)
                    if !selection.isEmpty {
                        Section {
                            ForEach(selection) { stage in stageRow(stage, activity: activity) }
                        } header: {
                            // 참여 중 is announced on `활동 일정` below instead of here, where every stage is already 통과.
                            header("선발 과정", badge: progress.participating ? nil : progress.badge(now: Date()), danger: progress.failed)
                        } footer: {
                            Text("표시는 내가 남긴 것이며 주최 측 결과가 아니에요.")
                        }
                    }
                    if !activities.isEmpty {
                        Section {
                            ForEach(activities) { stage in stageRow(stage, activity: activity) }
                        } header: {
                            header("활동 일정", badge: progress.participating ? "참여 중" : (selection.isEmpty ? progress.badge(now: Date()) : nil),
                                   danger: progress.failed)
                        }
                    }
                    Section {
                        Button("내 활동에서 빼기", role: .destructive) { removing = activityID }
                            .accessibilityIdentifier("mine-remove")
                    }
                }
                .listStyle(.insetGrouped)
            } else {
                CenteredMessage(symbol: "checklist", title: "진행 단계가 없는 활동이에요", message: "일정이 등록되면 단계가 여기에 보여요.")
            }
        }
        .navigationTitle("진행 단계").navigationBarTitleDisplayMode(.inline).analyticsScreen("activity_progress")
        .toolbar(.visible, for: .navigationBar)
        .tint(DearbyStyle.teal)
        .sheet(item: $calendarStage) { stage in
            CalendarConflictView(schedules: stage.overlapSchedules, activityLink: state.calendarLink(activityID))
        }
        .sheet(item: $calendarDraft) { AddToCalendarSheet(draft: $0).ignoresSafeArea() }
        .timetableSheet($timetable, state: state)
        // After removal the timeline closes and the list shows again (contract "빼기는 눈에 보이게").
        .removeFromMineDialog($removing, state: state) { dismiss() }
    }
    /// A section header read as a heading, with the state badge at its trailing end when there is one.
    private func header(_ title: String, badge: String?, danger: Bool) -> some View {
        HStack {
            Text(title).accessibilityAddTraits(.isHeader)
            Spacer()
            if let badge { DearbyBadge(title: badge, danger: danger).textCase(nil) }
        }
    }
    /// The discovery card as it looks there, without the `+` (the activity is already here).
    private func card(_ activity: ActivityModel) -> some View {
        let recruitmentDate = activity.recruitmentDate(at: state.loadedAt)
        return ActivityCard(activityID: activity.id, title: activity.title, status: activity.status(at: state.loadedAt),
                            recruitmentText: recruitmentDate.text, recruitmentDDay: recruitmentDate.dday, scheduleText: activity.scheduleText(at: state.loadedAt),
                            place: activity.place?.name ?? activity.location, fieldTag: activity.fieldTags.first, traitTags: activity.traitTags,
                            surface: Color(.secondarySystemGroupedBackground), hasTimetable: activity.hasTimetable) {
            FavoriteControls(target: .activity(activity.id))
        } open: {
            openDetail()
        }
    }
    /// One stage: a plain row, or a menu when it has something to choose or do.
    @ViewBuilder private func stageRow(_ stage: ActivityStage, activity: ActivityModel) -> some View {
        let draft = stage.calendarDraft(activity, link: state.calendarLink(activity.id))
        if stage.selection || draft != nil || !stage.overlapSchedules.isEmpty || stage.source.hasTimetable {
            Menu {
                // The block's 트랙·세션 시간표 first (user 2026-10-26 "펼쳤을 때 시간표 보기 버튼"): the same sheet as the activity detail.
                if stage.source.hasTimetable {
                    Button("시간표 보기", systemImage: "tablecells") {
                        timetable = TimetableRoute(activityID: activity.id, activityTitle: activity.title, scheduleID: stage.source.id,
                                                   scheduleTitle: stage.name, date: stage.dateText(now: Date()))
                    }
                }
                if stage.selection {
                    Picker("\(stage.name) 표시", selection: Binding(get: { stage.status },
                                                                 set: { state.markStage(activityID, stage.source.id, StageMark($0)) })) {
                        ForEach(stage.source.statuses, id: \.self) { Text($0.name).tag($0) }
                    }.pickerStyle(.inline)
                }
                if let draft { Button("캘린더에 추가", systemImage: "calendar.badge.plus") { calendarDraft = draft } }
                if !stage.overlapSchedules.isEmpty { Button("겹치는 시간 확인", systemImage: "calendar.badge.clock") { calendarStage = stage } }
            } label: {
                stageLabel(stage, menu: true)
            }
            .accessibilityHint(stage.selection ? "누르면 표시를 고르거나 캘린더에 추가해요" : "누르면 시간표를 보거나 캘린더에 추가해요")
        } else {
            stageLabel(stage, menu: false)
        }
    }
    private func stageLabel(_ stage: ActivityStage, menu: Bool) -> some View {
        HStack(spacing: 12) {
            Image(systemName: stage.icon).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(iconColor(stage)).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(stage.name).font(.dearby(.body).weight(stage.state == .current ? .semibold : .regular)).foregroundStyle(.primary)
                Text(stage.dateText(now: Date()) + " · " + timeWord(stage)).font(.dearby(.footnote)).foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            // 시간표 있음 (user 2026-10-26): the timetable glyph before the status, secondary.
            if stage.source.hasTimetable {
                Image(systemName: "tablecells").font(.dearby(.subheadline)).foregroundStyle(.secondary).accessibilityHidden(true)
            }
            if stage.selection { status(stage) }
            if menu { Image(systemName: "chevron.up.chevron.down").font(.dearby(.caption).weight(.semibold)).foregroundStyle(.tertiary) }
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([stage.name, stage.dateText(now: Date()), timeWord(stage), stage.selection ? stage.status.name : nil,
                             stage.source.hasTimetable ? "시간표 있음" : nil]
            .compactMap { $0 }.joined(separator: ", "))
    }
    private func iconColor(_ stage: ActivityStage) -> Color {
        if stage.mark.kind == .failed { return DearbyStyle.danger }
        switch stage.state {
        case .current: return DearbyStyle.teal
        case .past: return .secondary
        case .upcoming: return Color(.tertiaryLabel)
        }
    }
    /// The time judgment: 지남, 진행 중 (선발 단계 only, as before) or 예정.
    private func timeWord(_ stage: ActivityStage) -> String {
        switch stage.state {
        case .past: "지남"
        case .current where stage.selection: "진행 중"
        default: "예정"
        }
    }
    /// The marked status of a 선발 단계: 통과 in teal, 탈락 in the danger colour, the rest (대기, admin names) secondary.
    private func status(_ stage: ActivityStage) -> some View {
        Text(stage.status.name).font(.dearby(.subheadline).weight(.semibold))
            .foregroundStyle(stage.mark.kind == .failed ? DearbyStyle.danger : (stage.mark.kind == .passed ? DearbyStyle.teal : .secondary))
    }
}
