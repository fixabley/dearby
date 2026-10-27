import SwiftUI

/// 오늘 내 행사 card (contract "오늘 내 행사 카드" 2026-10-26) at the top of 발견, above the queue row: the same grouped card as the program cards, a
/// teal `오늘`/`D-n` badge before the activity name, `외 n개` when more qualify, and the next session or block under it. The card opens the block's
/// timetable (today, favorites first) or the progress track (`open`); today also offers `명함 QR` (`showQR`), the card QR with this activity.
struct TodayEventCard: View {
    let event: TodayEvent
    let now: Date
    let open: () -> Void
    let showQR: () -> Void
    private var today: Bool { event.timing == .today }
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            row
            // 행사 안내 시작 (contract "행사 당일 진행 중 표시" 2026-10-27): only today, with 내 시간표 sessions of this activity left.
            if today { LiveGuideButton(activityID: event.activity.id) }
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 20, style: .continuous))
    }
    private var row: some View {
        HStack(alignment: .center, spacing: 12) {
            Button(action: open) {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(event.badge).font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.onTeal)
                            .padding(.horizontal, 8).padding(.vertical, 3).background(DearbyStyle.tealFill, in: Capsule())
                        Text(event.activity.title).font(.dearby(.headline)).foregroundStyle(.primary).lineLimit(2).multilineTextAlignment(.leading)
                        if event.others > 0 { Text("외 \(event.others)개").font(.dearby(.footnote)).foregroundStyle(.secondary).lineLimit(1) }
                    }
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(event.detail(now: now)).font(.dearby(.subheadline)).foregroundStyle(today ? .primary : .secondary)
                            .lineLimit(2).multilineTextAlignment(.leading)
                        Image(systemName: "chevron.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(.tertiary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel([event.title, event.others > 0 ? "외 \(event.others)개" : nil, event.detail(now: now)].compactMap { $0 }.joined(separator: ", "))
            .accessibilityHint(today && event.block.hasTimetable ? "누르면 이 일정의 시간표를 보여요" : "누르면 진행 단계를 보여요")
            .accessibilityIdentifier("today-card")
            if today {
                Button(action: showQR) {
                    Label("명함 QR", systemImage: "qrcode").font(.dearby(.subheadline).weight(.semibold)).labelStyle(.titleAndIcon)
                }
                .buttonStyle(.bordered).controlSize(.regular).tint(DearbyStyle.teal)
                .accessibilityHint("내 명함의 QR을 이 활동으로 보여요").accessibilityIdentifier("today-card-qr")
            }
        }
    }
}
