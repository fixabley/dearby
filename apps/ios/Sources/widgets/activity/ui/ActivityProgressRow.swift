import SwiftUI

/// One applied activity with its segment track and next-stage badge (contract "내 활동 진행 트랙 — 2026-10-18"). `open` shows the
/// timeline; a plain button, not a `NavigationLink`, so the 내 활동 `List` adds no chevron of its own.
struct ActivityProgressRow: View {
    let activity: ActivityModel
    let progress: ActivityProgress
    let now: Date
    let open: () -> Void
    var body: some View {
        Button(action: open) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(activity.title).font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink).lineLimit(2)
                        Text([activity.program, activity.organization].compactMap { $0 }.joined(separator: " · "))
                            .font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet).lineLimit(1)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                    DearbyBadge(title: progress.badge(now: now), danger: progress.failed)
                }
                StageTrack(progress: progress)
            }
            .padding(14).background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 14, style: .continuous))
        }.buttonStyle(.plain).accessibilityIdentifier("activity-\(activity.id)")
    }
}

/// Up to four segments (past filled, current outlined, upcoming grey, 탈락 red) with `+N` beyond, names underneath.
/// One accessibility element; nothing animates, so reduced motion needs no special case.
struct StageTrack: View {
    let progress: ActivityProgress
    private static let maxSegments = 4
    private var shown: [ActivityStage] { Array(progress.stages.prefix(Self.maxSegments)) }
    private var hidden: Int { progress.stages.count - shown.count }
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 4) {
                ForEach(shown) { segment($0).frame(height: 6) }
                if hidden > 0 { Text("+\(hidden)").font(.dearby(.caption2)).foregroundStyle(DearbyStyle.quiet) }
            }
            HStack(spacing: 4) {
                ForEach(shown) { stage in
                    Text(stage.name).font(.dearby(.caption2).weight(stage.state == .current ? .bold : .regular))
                        .foregroundStyle(stage.state == .current ? DearbyStyle.ink : DearbyStyle.quiet)
                        .lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                }
                // Keeps the names under their segments.
                if hidden > 0 { Text("+\(hidden)").font(.dearby(.caption2)).hidden() }
            }
        }
        .accessibilityElement(children: .ignore).accessibilityLabel(progress.accessibilityLabel)
    }
    @ViewBuilder private func segment(_ stage: ActivityStage) -> some View {
        let shape = RoundedRectangle(cornerRadius: 3)
        if stage.mark.kind == .failed {
            shape.fill(DearbyStyle.danger)
        } else {
            switch stage.state {
            case .past: shape.fill(DearbyStyle.teal)
            case .current: shape.fill(DearbyStyle.mint).overlay(shape.stroke(DearbyStyle.teal, lineWidth: 1.5))
            case .upcoming: shape.fill(Color(.opaqueSeparator))
            }
        }
    }
}
