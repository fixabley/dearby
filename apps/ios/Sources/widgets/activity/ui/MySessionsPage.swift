import SwiftUI

/// 즐겨찾기 › 찜한 세션, equal for every push.
struct MySessionsRoute: Hashable {}

/// 찜한 세션 outside an activity (contract "트랙·세션 시간표" 2026-10-26): `GET /v1/me/session-favorites` grouped by activity, then by day, each
/// day as 나만의 시간표 (`MyTimetableList`). The list carries no track names, so each block's timetable is read once for its tracks and
/// zone; a block whose timetable cannot be read still lists its sessions, without the track badge.
struct MySessionsPage: View {
    let state: ActivityViewModel
    @Environment(SessionFavoritesModel.self) private var favorites
    @State private var timetables: [String: Timetable] = [:]
    @State private var opened: FavoriteSession?
    var body: some View {
        Group {
            if favorites.items.isEmpty {
                CenteredMessage(symbol: "heart", title: "찜한 세션이 없어요", message: "활동 상세의 시간표에서 하트를 누르면 여기에 모여요.")
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
                            MyTimetableList(items: group.items.map { MyTimetable.Item(session: $0.session, track: track($0)) }, zone: group.zone) { session in
                                opened = group.items.first { $0.session.id == session.id }
                            }
                        }
                    }.padding(16)
                }
                .refreshable { await favorites.load() }
            }
        }
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .navigationTitle("찜한 세션").navigationBarTitleDisplayMode(.inline).analyticsScreen("session_favorites")
        .task(id: blocks) { await loadTimetables() }
        .sheet(item: $opened) { item in TimetableSessionSheet(session: item.session, track: track(item), zone: zone(item)) }
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
                let day = ActivityText.format(item.session.start, "yyyy-MM-dd", zone: zone(item))
                if byDay[day] == nil { days.append(day) }
                byDay[day, default: []].append(item)
            }
            return days.map { day in
                let dayItems = byDay[day] ?? []
                return DayGroup(id: activityID + "|" + day, activityName: dayItems[0].activityName, zone: zone(dayItems[0]), items: dayItems)
            }
        }
    }
    private var blocks: [String] { Array(Set(favorites.items.map { $0.activityID + "|" + $0.scheduleID })).sorted() }
    private func track(_ item: FavoriteSession) -> TimetableTrack? { timetables[item.activityID + "|" + item.scheduleID]?.track(item.session.trackID) }
    private func zone(_ item: FavoriteSession) -> String { timetables[item.activityID + "|" + item.scheduleID]?.zone ?? "Asia/Seoul" }
    private func loadTimetables() async {
        for key in blocks where timetables[key] == nil {
            let parts = key.split(separator: "|").map(String.init)
            guard parts.count == 2, let timetable = try? await state.timetable(parts[0], parts[1]) else { continue }
            timetables[key] = timetable
        }
    }
}
