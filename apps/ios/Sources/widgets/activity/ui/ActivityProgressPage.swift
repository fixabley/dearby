import SwiftUI

/// Route to an applied activity's timeline; the activity detail keeps the plain String route.
struct ActivityProgressRoute: Hashable {
    let activityID: String
}

/// Vertical timeline of one applied activity's stages with the user's 통과/대기/탈락 marks (contract "내 활동 진행 트랙 — 2026-10-18").
struct ActivityProgressPage: View {
    let state: ActivityViewModel
    let activityID: String
    @State private var calendarStage: ActivityStage?
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
        .navigationTitle("진행 단계").navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .background(Color(.systemBackground)).tint(DearbyStyle.teal)
        .sheet(item: $calendarStage) { stage in
            if let schedule = stage.schedule { CalendarConflictView(schedules: [schedule]) }
        }
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
            // The participation mark stays reachable here: the track row replaced the plain row that carried it.
            ConfirmToggle(state: state, activityID: activityID).font(.dearby(.subheadline))
            Text(ConfirmToggle.note).font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
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
                Picker("\(stage.name) 표시", selection: Binding(get: { state.stageMark(activityID, stage.id) },
                                                              set: { state.markStage(activityID, stage.id, $0) })) {
                    ForEach(StageMark.allCases, id: \.self) { Text($0.label).tag($0) }
                }.pickerStyle(.segmented)
                if stage.schedule != nil {
                    Button("겹치는 시간 확인") { calendarStage = stage }
                        .font(.dearby(.subheadline).weight(.semibold)).foregroundStyle(DearbyStyle.teal).frame(minHeight: 44)
                }
            }.padding(.bottom, last ? 0 : 20)
        }.fixedSize(horizontal: false, vertical: true)
    }
    /// 탈락·통과 show the user's mark; otherwise the time judgment.
    private func chip(_ stage: ActivityStage) -> some View {
        let text: String
        let strong: Bool
        switch (stage.mark, stage.state) {
        case (.fail, _): (text, strong) = ("탈락", false)
        case (.pass, _): (text, strong) = ("통과", true)
        case (_, .past): (text, strong) = ("지남", false)
        case (_, .current): (text, strong) = ("진행 중", true)
        case (_, .upcoming): (text, strong) = ("예정", false)
        }
        return Text(text).font(.dearby(.caption).weight(.semibold))
            .foregroundStyle(stage.mark == .fail ? DearbyStyle.danger : (strong ? DearbyStyle.teal : DearbyStyle.quiet))
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(strong ? DearbyStyle.mint : DearbyStyle.muted, in: Capsule())
    }
}
