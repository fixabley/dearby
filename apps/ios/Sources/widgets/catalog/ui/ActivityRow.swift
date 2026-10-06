import SwiftUI

struct ActivityRow<Footer: View>: View {
    let activity: ActivityModel
    @ViewBuilder var footer: Footer
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            NavigationLink(value: activity.id) {
                HStack(alignment: .top, spacing: 12) {
                    ActivityArtwork(activityID: activity.id).frame(width: 104, height: 112).clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 9))
                    VStack(alignment: .leading, spacing: 7) {
                        Text(activity.title).font(.headline).foregroundStyle(Color.primary)
                        Text(activity.organization ?? activity.summary).font(.caption).foregroundStyle(DearbyStyle.quiet).lineLimit(2)
                        Divider()
                        Label(activity.dateLabel.isEmpty ? "일정 미확인" : activity.dateLabel, systemImage: "calendar")
                        Label(activity.location ?? "장소 미확인", systemImage: "mappin.and.ellipse")
                    }.font(.caption).foregroundStyle(DearbyStyle.quiet).frame(maxWidth: .infinity, alignment: .leading)
                }
            }.buttonStyle(.plain).accessibilityIdentifier("activity-\(activity.id)")
            footer
        }
        .padding(10).background(.white, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.line))
    }
}

extension ActivityRow where Footer == EmptyView {
    init(activity: ActivityModel) { self.init(activity: activity) { EmptyView() } }
}

/// Shown wherever the participation mark can be changed.
struct ConfirmToggle: View {
    static let note = "참여 확정 표시는 내가 남기는 예시 표시예요. 주최 측 확정이 아니에요."
    let state: CatalogViewModel
    let activityID: String
    var body: some View {
        Toggle("참여 확정 표시", isOn: Binding(get: { state.confirmedIDs.contains(activityID) },
                                         set: { state.confirm(activityID, $0) }))
            .tint(DearbyStyle.teal).frame(minHeight: 44).accessibilityIdentifier("confirm-\(activityID)")
    }
}
