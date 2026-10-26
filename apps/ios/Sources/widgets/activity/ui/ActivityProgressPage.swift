import SwiftUI

/// Route to an applied activity's timeline; the activity detail keeps the plain String route.
struct ActivityProgressRoute: Hashable {
    let activityID: String
}

/// 진행 단계 (contract "내 활동 진행 트랙 — 2026-10-18", 정리 "진행 단계 화면 정리" 2026-10-26): an inset-grouped `List` in three sections with
/// system separators. `활동 정보` is the discovery `ActivityCard` (opens the detail) with the program and organization under it; `진행 단계`
/// has one row per stage (icon, name, date · 지남/진행 중/예정, and on the right the marked status of a 선발 단계 only — 2026-10-26) whose
/// header names the current stage (`D-3 · 면접`); pressing a row opens its menu (the schedule's statuses, contract "진행 단계 상태" 2026-10-22,
/// `캘린더에 추가`, `겹치는 시간 확인`). `시간표` lists the blocks with a 트랙·세션 시간표 as rows that open the timetable sheet. `내 활동에서 빼기`
/// is the last row. A FAILED kind ends the track.
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
                    Section {
                        ForEach(progress.stages) { stage in stageRow(stage, activity: activity) }
                    } header: {
                        HStack {
                            Text("진행 단계")
                            Spacer()
                            DearbyBadge(title: progress.badge(now: Date()), danger: progress.failed).textCase(nil)
                        }
                    } footer: {
                        Text("표시는 내가 남긴 것이며 주최 측 결과가 아니에요.")
                    }
                    let blocks = progress.stages.filter { $0.id != ActivityStages.applicationStageID && $0.source.hasTimetable }
                    if !blocks.isEmpty {
                        Section("시간표") {
                            ForEach(blocks) { stage in timetableRow(stage, activity: activity) }
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
        if stage.selection || draft != nil || !stage.overlapSchedules.isEmpty {
            Menu {
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
            .accessibilityHint(stage.selection ? "누르면 표시를 고르거나 캘린더에 추가해요" : "누르면 캘린더에 추가하거나 겹침을 확인해요")
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
            if stage.selection { status(stage) }
            if menu { Image(systemName: "chevron.up.chevron.down").font(.dearby(.caption).weight(.semibold)).foregroundStyle(.tertiary) }
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([stage.name, stage.dateText(now: Date()), timeWord(stage), stage.selection ? stage.status.name : nil]
            .compactMap { $0 }.joined(separator: ", "))
    }
    /// A block with a timetable: the same row shape, opening the sheet.
    private func timetableRow(_ stage: ActivityStage, activity: ActivityModel) -> some View {
        Button {
            timetable = TimetableRoute(activityID: activity.id, activityTitle: activity.title, scheduleID: stage.source.id,
                                       scheduleTitle: stage.name, date: stage.dateText(now: Date()))
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "tablecells").font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal).frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(stage.name).font(.dearby(.body)).foregroundStyle(.primary)
                    Text(stage.dateText(now: Date())).font(.dearby(.footnote)).foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(.tertiary)
            }.contentShape(Rectangle())
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(stage.name) 시간표, \(stage.dateText(now: Date()))").accessibilityHint("누르면 시간표를 보여요")
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("timetable-\(stage.source.id)")
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
