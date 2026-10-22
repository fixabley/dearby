import SwiftUI

/// A stage-less 내 활동 row: `open` shows the detail (a plain button so the `List` adds no chevron or whole-row tap of its own).
struct ActivityRow<Footer: View>: View {
    let activity: ActivityModel
    let open: () -> Void
    @ViewBuilder var footer: Footer
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button(action: open) {
                HStack(alignment: .top, spacing: 12) {
                    ActivityArtwork(imageURL: activity.imageURL).frame(width: 104, height: 112).clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 9))
                    VStack(alignment: .leading, spacing: 7) {
                        Text(activity.title).font(.headline).foregroundStyle(Color.primary)
                        Text(activity.organization).font(.caption).foregroundStyle(DearbyStyle.quiet).lineLimit(2)
                        Divider()
                        Label(activity.scheduleText(at: Date()) ?? "일정 미확인", systemImage: "calendar")
                    }.font(.caption).foregroundStyle(DearbyStyle.quiet).frame(maxWidth: .infinity, alignment: .leading)
                }
            }.buttonStyle(.plain).accessibilityIdentifier("activity-\(activity.id)")
            footer
        }
        .padding(10).background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 14, style: .continuous))
    }
}
