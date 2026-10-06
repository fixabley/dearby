import SwiftUI

/// Applied activities in schedule order, each with the user's own participation mark.
struct MyActivitiesPage: View {
    let state: CatalogViewModel
    let explore: () -> Void
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack { Text("내 활동").font(.title2.bold()); DearbyBadge(title: "예시") }
                if state.appliedActivities.isEmpty {
                    ContentUnavailableView {
                        Label("신청한 활동이 없어요", systemImage: "calendar")
                    } description: {
                        Text("발견에서 관심 있는 활동을 신청해 보세요.")
                    } actions: {
                        Button("활동 둘러보기", action: explore).accessibilityIdentifier("explore")
                    }
                } else {
                    Text(ConfirmToggle.note).font(.footnote).foregroundStyle(DearbyStyle.quiet)
                    ForEach(state.appliedActivities) { activity in
                        ActivityRow(activity: activity) {
                            DearbyBadge(title: state.confirmedIDs.contains(activity.id) ? "참여 확정" : "신청함")
                            ConfirmToggle(state: state, activityID: activity.id)
                        }
                    }
                }
            }.padding(20)
        }
        .navigationDestination(for: String.self) { id in ActivityDetailView(state: state, activityID: id) }
        .scrollContentBackground(.hidden).background(.white)
        .tint(DearbyStyle.teal).navigationTitle("")
        .navigationBarTitleDisplayMode(.inline).toolbar(.hidden, for: .navigationBar)
    }
}
