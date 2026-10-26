import SwiftUI

/// The block whose timetable the activity detail opens in a sheet when its schedule row is pressed.
struct TimetableRoute: Identifiable {
    let activityID: String
    let activityTitle: String
    let scheduleID: String
    let scheduleTitle: String
    /// The block's date and time, as on the schedule row.
    let date: String
    var id: String { activityID + "/" + scheduleID }
}

/// 시간표 (contract "트랙·세션 시간표" 2026-10-26 모달 시트): the sheet's content, titled `{활동 이름} · {블록 제목}` over the block's time, with
/// `닫기` and `전체 | 찜한 세션` at the top. 전체 is the table (`TimetableTable`); 찜한 세션 is 나만의 시간표 (`MyTimetableList`). Loads when the
/// sheet opens; a session opens its own sheet. Read without a token; the hearts follow `SessionFavoritesModel`.
struct TimetablePage: View {
    let state: ActivityViewModel
    let route: TimetableRoute
    enum Mode: Hashable { case all, favorites }
    @State private var mode = Mode.all
    @State private var phase = Phase.loading
    @State private var opened: TimetableSession?
    @Environment(SessionFavoritesModel.self) private var favorites
    @Environment(\.dismiss) private var dismiss
    private enum Phase { case loading, missing, failed, loaded(Timetable) }
    var body: some View {
        content
            .safeAreaInset(edge: .top, spacing: 0) {
                Picker("보기", selection: $mode) {
                    Text("전체").tag(Mode.all)
                    Text("찜한 세션").tag(Mode.favorites)
                }
                .pickerStyle(.segmented).padding(.horizontal, 16).padding(.vertical, 8).background(.bar)
                .accessibilityIdentifier("timetable-mode")
            }
            .background(Color(.systemGroupedBackground).ignoresSafeArea())
            .navigationTitle(route.activityTitle + " · " + route.scheduleTitle).navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 0) {
                        Text(route.activityTitle + " · " + route.scheduleTitle).font(.dearby(.headline)).lineLimit(1)
                        Text(route.date).font(.dearby(.caption)).foregroundStyle(.secondary).lineLimit(1)
                    }
                    .accessibilityElement(children: .combine).accessibilityAddTraits(.isHeader)
                }
                ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } }
            }
            .analyticsScreen("timetable")
            .task { if case .loading = phase { await load() } }
            .sheet(item: $opened) { session in
                if case .loaded(let timetable) = phase {
                    TimetableSessionSheet(session: session, track: timetable.track(session.trackID), zone: timetable.zone)
                }
            }
    }
    @ViewBuilder private var content: some View {
        switch phase {
        case .loading:
            ScrollView { DearbyRowsSkeleton(count: 5).padding(20) }.scrollDisabled(true)
        case .missing:
            CenteredMessage(symbol: "calendar", title: "시간표가 없어요", message: "이 일정의 시간표가 지워졌거나 아직 공개되지 않았어요.")
        case .failed:
            CenteredMessage(symbol: "exclamationmark.triangle", danger: true, title: "시간표를 불러오지 못했어요", message: "연결을 확인한 뒤 다시 시도해 주세요.",
                            primary: MessageAction("다시 시도") { phase = .loading; Task { await load() } })
        case .loaded(let timetable):
            switch mode {
            case .all:
                ScrollView(.vertical) {
                    TimetableTable(timetable: timetable, overlaps: overlaps(timetable)) { opened = $0 }.padding(.vertical, 12)
                }
            case .favorites: favoritesView(timetable)
            }
        }
    }
    @ViewBuilder private func favoritesView(_ timetable: Timetable) -> some View {
        let mine = timetable.sessions.filter { favorites.ids.contains($0.id) }
        if favorites.phase == .signedOut {
            CenteredMessage(symbol: "heart", title: "찜한 세션은 계정에 모여요", message: "로그인하면 듣고 싶은 세션을 찜해 나만의 시간표로 볼 수 있어요.",
                            primary: MessageAction("로그인") { favorites.signInRequested = true })
        } else if mine.isEmpty {
            CenteredMessage(symbol: "heart", title: "찜한 세션이 없어요", message: "전체 시간표에서 하트를 누르면 여기에 나만의 시간표로 모여요.")
        } else {
            ScrollView {
                MyTimetableList(items: mine.map { MyTimetable.Item(session: $0, track: timetable.track($0.trackID)) }, zone: timetable.zone) { opened = $0 }
                    .padding(16)
            }
        }
    }
    /// Favorited sessions and how many favorites (itself included) share their time.
    private func overlaps(_ timetable: Timetable) -> [Int64: Int] {
        let mine = timetable.sessions.filter { favorites.ids.contains($0.id) }
        return Dictionary(uniqueKeysWithValues: mine.map { session in (session.id, mine.filter { $0.overlaps(session) }.count) })
    }
    private func load() async {
        do {
            phase = try await state.timetable(route.activityID, route.scheduleID).map(Phase.loaded) ?? .missing
        } catch {
            phase = .failed
        }
    }
}

/// The table (contract "표"): the time column stays while the whole grid scrolls sideways at once, so columns stay aligned. Without
/// tracks, one card per time slot and no sideways scrolling. Columns widen to fill a wide screen (no scrolling when they all fit).
struct TimetableTable: View {
    let timetable: Timetable
    /// Favorited session → favorites at the same time, itself included.
    let overlaps: [Int64: Int]
    let open: (TimetableSession) -> Void
    @ScaledMetric(relativeTo: .body) private var rowHeight: CGFloat = 168
    @ScaledMetric(relativeTo: .body) private var breakHeight: CGFloat = 52
    @ScaledMetric(relativeTo: .body) private var minColumn: CGFloat = 196
    @ScaledMetric(relativeTo: .body) private var timeWidth: CGFloat = 58
    @ScaledMetric(relativeTo: .body) private var headerHeight: CGFloat = 40
    @State private var width: CGFloat = 0
    private let gap: CGFloat = 6
    var body: some View {
        let grid = TimetableGrid(timetable)
        if timetable.tracks.isEmpty {
            singleColumn(grid)
        } else {
            let heights = grid.rows.indices.map { height(grid, row: $0) }
            let column = max(minColumn, (width - timeWidth - 16 - CGFloat(grid.columns) * gap) / CGFloat(grid.columns))
            HStack(alignment: .top, spacing: 0) {
                VStack(alignment: .trailing, spacing: gap) {
                    Color.clear.frame(height: headerHeight)
                    ForEach(grid.rows.indices, id: \.self) { row in
                        timeLabel(grid.rows[row]).frame(height: heights[row], alignment: .top)
                    }
                }
                .frame(width: timeWidth).padding(.leading, 8)
                ScrollView(.horizontal) {
                    table(grid, heights: heights, column: column).padding(.horizontal, gap)
                }
                .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
            }
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
        }
    }
    /// A row only of 쉬는시간·점심 is a thin band; any other row is tall enough for a card.
    private func height(_ grid: TimetableGrid, row: Int) -> CGFloat {
        let here = grid.cells.filter { $0.rows.contains(row) }
        return here.allSatisfy { $0.session.isBreak && $0.column == nil } && !here.isEmpty ? breakHeight : rowHeight
    }
    private func table(_ grid: TimetableGrid, heights: [CGFloat], column: CGFloat) -> some View {
        let top = heights.indices.map { row in headerHeight + gap + heights.prefix(row).reduce(0, +) + CGFloat(row) * gap }
        func span(_ rows: Range<Int>) -> CGFloat { heights[rows].reduce(0, +) + CGFloat(rows.count - 1) * gap }
        func left(_ index: Int) -> CGFloat { CGFloat(index) * (column + gap) }
        let total = CGFloat(grid.columns) * (column + gap) - gap
        return ZStack(alignment: .topLeading) {
            ForEach(timetable.tracks.indices, id: \.self) { index in
                TimetableTrackBadge(track: timetable.tracks[index]).frame(width: column, height: headerHeight, alignment: .leading)
                    .offset(x: left(index)).accessibilityAddTraits(.isHeader)
            }
            ForEach(grid.empty, id: \.self) { slot in
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color(.tertiarySystemFill).opacity(0.5))
                    .frame(width: column, height: heights[slot.row]).offset(x: left(slot.column), y: top[slot.row]).accessibilityHidden(true)
            }
            ForEach(grid.cells) { cell in
                TimetableSessionCard(session: cell.session, track: timetable.track(cell.session.trackID), zone: timetable.zone,
                                     overlapCount: overlaps[cell.session.id] ?? 0) { open(cell.session) }
                    .frame(width: cell.column == nil ? total : column, height: span(cell.rows))
                    .offset(x: left(cell.column ?? 0), y: top[cell.rows.lowerBound])
            }
        }
        .frame(width: total, height: (top.last ?? headerHeight) + (heights.last ?? 0), alignment: .topLeading)
    }
    private func timeLabel(_ row: TimetableGrid.Row) -> some View {
        VStack(alignment: .trailing, spacing: 0) {
            Text(ActivityText.time(row.start, zone: timetable.zone))
            Text(ActivityText.time(row.end, zone: timetable.zone)).foregroundStyle(.secondary)
        }
        .font(.dearby(.caption).weight(.semibold)).monospacedDigit().padding(.trailing, 6).padding(.top, 4)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(ActivityText.time(row.start, zone: timetable.zone) + "부터 " + ActivityText.time(row.end, zone: timetable.zone) + "까지")
    }
    /// No tracks: the time band and one card per session, no sideways scrolling.
    private func singleColumn(_ grid: TimetableGrid) -> some View {
        VStack(spacing: gap) {
            ForEach(grid.cells) { cell in
                HStack(alignment: .top, spacing: 0) {
                    timeLabel(TimetableGrid.Row(start: cell.session.start, end: cell.session.end)).frame(width: timeWidth, alignment: .trailing)
                    TimetableSessionCard(session: cell.session, track: nil, zone: timetable.zone, overlapCount: overlaps[cell.session.id] ?? 0) {
                        open(cell.session)
                    }
                    .frame(minHeight: cell.session.isBreak ? breakHeight : nil)
                }
            }
        }.padding(.horizontal, 8)
    }
}

/// 나만의 시간표 (contract "트랙·세션 시간표" 2026-10-26 `찜한 세션`): the favorited sessions in time order, one per line with its time and a large
/// track·room badge (where to go), the free time and the move between them, and a warning band before sessions favorited at the same time.
struct MyTimetableList: View {
    let items: [MyTimetable.Item]
    let zone: String
    let open: (TimetableSession) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(MyTimetable.entries(items).enumerated()), id: \.offset) { _, entry in
                switch entry {
                case .session(let item):
                    TimetableSessionCard(session: item.session, track: item.track, zone: zone, showsTime: true, largeTrack: true) { open(item.session) }
                case .overlap(let count):
                    Label("같은 시간에 찜한 세션 \(count)개 · 하나만 들을 수 있어요", systemImage: "exclamationmark.triangle.fill")
                        .font(.dearby(.footnote).weight(.semibold)).foregroundStyle(DearbyStyle.danger)
                        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                        .background(DearbyStyle.danger.opacity(0.12), in: .rect(cornerRadius: 10, style: .continuous))
                case .gap(let minutes, let moveTo):
                    gap(minutes, moveTo)
                }
            }
        }
    }
    private func gap(_ minutes: Int, _ moveTo: TimetableTrack?) -> some View {
        let free = minutes >= 60 ? "\(minutes / 60)시간" + (minutes % 60 > 0 ? " \(minutes % 60)분" : "") : "\(minutes)분"
        let text = [minutes > 0 ? free + " 비어 있어요" : nil,
                    moveTo.map { "이동 › " + [$0.name, $0.room].compactMap { $0 }.joined(separator: " · ") }].compactMap { $0 }.joined(separator: " · ")
        return Label(text, systemImage: moveTo != nil ? "figure.walk" : "clock")
            .font(.dearby(.footnote)).foregroundStyle(.secondary).padding(.horizontal, 8).padding(.vertical, 2)
    }
}
