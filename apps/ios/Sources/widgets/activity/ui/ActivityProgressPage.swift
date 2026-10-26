import SwiftUI

/// Route to an applied activity's timeline; the activity detail keeps the plain String route.
struct ActivityProgressRoute: Hashable {
    let activityID: String
}

/// Vertical timeline of one applied activity's stages with the user's marks (contract "내 활동 진행 트랙 — 2026-10-18"); each stage offers
/// its schedule's statuses (default 대기·통과·탈락, or the admin's, contract "진행 단계 상태" 2026-10-22) — 선발 단계 only (2026-10-26); other
/// stages show the date and 예정/지남. A FAILED kind ends the track.
struct ActivityProgressPage: View {
    let state: ActivityViewModel
    let activityID: String
    @State private var calendarStage: ActivityStage?
    @State private var calendarDraft: CalendarEventDraft?
    @State private var removing: String?
    @Environment(\.dismiss) private var dismiss
    private var activity: ActivityModel? { state.activity(activityID) }
    var body: some View {
        Group {
            if let activity, let progress = state.progress(activity, now: Date()) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        header(activity)
                        VStack(spacing: 0) {
                            ForEach(Array(progress.stages.enumerated()), id: \.element.id) { index, stage in
                                row(stage, last: index == progress.stages.count - 1)
                            }
                        }
                        Divider()
                        Text("표시는 내가 남긴 것이며 주최 측 결과가 아니에요.").font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
                        Button("내 활동에서 빼기", role: .destructive) { removing = activityID }
                            .buttonStyle(.borderless).font(.dearby(.body).weight(.semibold)).frame(minHeight: 44)
                            .accessibilityIdentifier("mine-remove")
                    }.padding(20)
                }
            } else {
                CenteredMessage(symbol: "checklist", title: "진행 단계가 없는 활동이에요", message: "일정이 등록되면 단계가 여기에 보여요.")
            }
        }
        .navigationTitle("진행 단계").navigationBarTitleDisplayMode(.inline).analyticsScreen("activity_progress")
        .toolbar(.visible, for: .navigationBar)
        .background(Color(.systemBackground)).tint(DearbyStyle.teal)
        .sheet(item: $calendarStage) { stage in
            CalendarConflictView(schedules: stage.overlapSchedules, activityLink: state.calendarLink(activityID))
        }
        .sheet(item: $calendarDraft) { AddToCalendarSheet(draft: $0).ignoresSafeArea() }
        // After removal the timeline closes and the list shows again (contract "빼기는 눈에 보이게").
        .removeFromMineDialog($removing, state: state) { dismiss() }
    }
    private func header(_ activity: ActivityModel) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(activity.title).font(.dearby(.title2).bold()).accessibilityAddTraits(.isHeader)
            Text([activity.program, activity.organization].compactMap { $0 }.joined(separator: " · "))
                .font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
            NavigationLink(value: activity.id) {
                Label("활동 상세 보기", systemImage: "info.circle").font(.dearby(.subheadline).weight(.semibold))
            }.frame(minHeight: 44)
        }
    }
    private func row(_ stage: ActivityStage, last: Bool) -> some View {
        HStack(alignment: .top, spacing: 14) {
            VStack(spacing: 0) {
                Image(systemName: stage.icon).font(.dearby(.caption).weight(.semibold))
                    .foregroundStyle(stage.state == .upcoming ? DearbyStyle.quiet : (stage.state == .past ? DearbyStyle.onTeal : DearbyStyle.teal))
                    .frame(width: 32, height: 32)
                    .background(stage.state == .past ? DearbyStyle.tealFill : (stage.state == .current ? DearbyStyle.mint : DearbyStyle.muted), in: Circle())
                    .overlay(Circle().stroke(stage.state == .current ? DearbyStyle.teal : .clear, lineWidth: 1.5))
                    .accessibilityHidden(true)
                if !last { Rectangle().fill(DearbyStyle.line).frame(width: 2).frame(maxHeight: .infinity) }
            }
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .firstTextBaseline) {
                    Text(stage.name).font(.dearby(.headline)).frame(maxWidth: .infinity, alignment: .leading)
                    chip(stage)
                }
                Text(stage.dateText(now: Date())).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
                if stage.selection { statusPicker(stage) }
                let draft = activity.flatMap { stage.calendarDraft($0, link: state.calendarLink($0.id)) }
                if !stage.overlapSchedules.isEmpty || draft != nil {
                    HStack(spacing: 20) {
                        if !stage.overlapSchedules.isEmpty { Button("겹치는 시간 확인") { calendarStage = stage } }
                        if let draft {
                            Button("캘린더에 추가", systemImage: "calendar.badge.plus") { calendarDraft = draft }
                                .accessibilityLabel("\(stage.name) 캘린더에 추가")
                        }
                    }
                    .buttonStyle(.borderless).font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal).frame(minHeight: 44)
                }
            }.padding(.bottom, last ? 0 : 20)
        }.fixedSize(horizontal: false, vertical: true)
    }
    /// The schedule's statuses: segmented up to three, a menu beyond (admin lists can be longer).
    @ViewBuilder private func statusPicker(_ stage: ActivityStage) -> some View {
        let options = stage.source.statuses
        let picker = Picker("\(stage.name) 표시", selection: Binding(get: { stage.status },
                                                                   set: { state.markStage(activityID, stage.source.id, StageMark($0)) })) {
            ForEach(options, id: \.self) { Text($0.name).tag($0) }
        }
        if options.count <= 3 { picker.pickerStyle(.segmented) } else { picker.pickerStyle(.menu).frame(minHeight: 44) }
    }
    /// A marked status other than waiting shows its name (탈락 in the danger colour); otherwise the time judgment. A stage that is not a
    /// 선발 단계 reads only 지남 or 예정.
    private func chip(_ stage: ActivityStage) -> some View {
        let text: String
        let strong: Bool
        switch (stage.mark.kind, stage.state) {
        case (_, .past) where !stage.selection: (text, strong) = ("지남", false)
        case _ where !stage.selection: (text, strong) = ("예정", false)
        case (.failed, _), (.other, _): (text, strong) = (stage.status.name, false)
        case (.passed, _): (text, strong) = (stage.status.name, true)
        case (_, .past): (text, strong) = ("지남", false)
        case (_, .current): (text, strong) = ("진행 중", true)
        case (_, .upcoming): (text, strong) = ("예정", false)
        }
        return Text(text).font(.dearby(.caption).weight(.semibold))
            .foregroundStyle(stage.mark.kind == .failed ? DearbyStyle.danger : (strong ? DearbyStyle.teal : DearbyStyle.quiet))
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(strong ? DearbyStyle.mint : DearbyStyle.muted, in: Capsule())
    }
}
