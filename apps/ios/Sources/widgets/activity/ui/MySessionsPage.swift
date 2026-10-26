import SwiftUI

/// 즐겨찾기 › 내 시간표, equal for every push.
struct MySessionsRoute: Hashable {}

/// 내 시간표 outside an activity (contract "트랙·세션 시간표" 2026-10-26): `GET /v1/me/session-favorites` grouped by activity, then by day, each
/// day as 내 시간표 (`MyTimetableList`). Each item carries its track and block zone, so no timetable is read again.
struct MySessionsPage: View {
    @Environment(SessionFavoritesModel.self) private var favorites
    @State private var opened: FavoriteSession?
    var body: some View {
        Group {
            if favorites.items.isEmpty {
                CenteredMessage(symbol: "heart", title: "내 시간표가 비어 있어요", message: "세션에 하트를 누르면 내 시간표에 모여요.")
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        ForEach(groups, id: \.id) { group in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(group.activityName).font(.dearby(.headline))
                                Text(ActivityText.format(group.items[0].session.start, "M월 d일 (E)", zone: group.zone))
                                    .font(.dearby(.subheadline)).foregroundStyle(.secondary)
                            }
                            .accessibilityElement(children: .combine).accessibilityAddTraits(.isHeader).padding(.top, 12)
                            MyTimetableList(items: group.items.map { MyTimetable.Item(session: $0.session, track: $0.track) }, zone: group.zone) { session in
                                opened = group.items.first { $0.session.id == session.id }
                            }
                        }
                    }.padding(16)
                }
                .refreshable { await favorites.load() }
            }
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .navigationTitle("내 시간표").navigationBarTitleDisplayMode(.inline).analyticsScreen("session_favorites")
        .sheet(item: $opened) { item in TimetableSessionSheet(session: item.session, track: item.track, zone: item.zone) }
    }
    private struct DayGroup: Identifiable {
        let id: String
        let activityName: String
        let zone: String
        let items: [FavoriteSession]
    }
    /// Activities in the order of their first session, then their days in order.
    private var groups: [DayGroup] {
        var order: [String] = []
        var byActivity: [String: [FavoriteSession]] = [:]
        for item in favorites.items.sorted(by: { $0.session.start < $1.session.start }) {
            if byActivity[item.activityID] == nil { order.append(item.activityID) }
            byActivity[item.activityID, default: []].append(item)
        }
        return order.flatMap { activityID -> [DayGroup] in
            let items = byActivity[activityID] ?? []
            var days: [String] = []
            var byDay: [String: [FavoriteSession]] = [:]
            for item in items {
                let day = ActivityText.format(item.session.start, "yyyy-MM-dd", zone: item.zone)
                if byDay[day] == nil { days.append(day) }
                byDay[day, default: []].append(item)
            }
            return days.map { day in
                let dayItems = byDay[day] ?? []
                return DayGroup(id: activityID + "|" + day, activityName: dayItems[0].activityName, zone: dayItems[0].zone, items: dayItems)
            }
        }
    }
}
