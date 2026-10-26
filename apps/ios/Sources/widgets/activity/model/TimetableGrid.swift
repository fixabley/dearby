import Foundation

/// The 시간표 table (contract "트랙·세션 시간표" 2026-10-26 "표"): rows are time slots, columns the tracks in `position` order. Rows are cut
/// at every session's start and end, so a pair shared by all tracks is one row and tracks with different bounds split the span into
/// finer rows that a longer session covers together (rowspan). Only slots some session covers are rows. A full-width session
/// (`trackId` null, or a track the response did not list) covers every column; a track column with nothing in a row is an empty cell.
struct TimetableGrid: Equatable {
    struct Row: Equatable {
        let start: Date
        let end: Date
    }
    struct Cell: Equatable, Identifiable {
        let session: TimetableSession
        let rows: Range<Int>
        /// The track's column; nil spans all of them.
        let column: Int?
        var id: Int64 { session.id }
    }
    let columns: Int
    let rows: [Row]
    /// In row, then column order (the reading order).
    let cells: [Cell]
    /// (row, column) slots no session fills.
    let empty: [EmptySlot]
    struct EmptySlot: Equatable, Hashable { let row: Int; let column: Int }

    init(_ timetable: Timetable) {
        let sessions = timetable.sessions.filter { $0.start < $0.end }
        let columnOf = Dictionary(timetable.tracks.enumerated().map { ($1.id, $0) }, uniquingKeysWith: { first, _ in first })
        let bounds = Array(Set(sessions.flatMap { [$0.start, $0.end] })).sorted()
        rows = zip(bounds, bounds.dropFirst()).compactMap { start, end in
            sessions.contains { $0.start <= start && $0.end >= end } ? Row(start: start, end: end) : nil
        }
        let rows = rows
        columns = max(timetable.tracks.count, 1)
        cells = sessions.compactMap { session in
            let covered = rows.indices.filter { rows[$0].start >= session.start && rows[$0].end <= session.end }
            guard let first = covered.first, let last = covered.last else { return nil }
            return Cell(session: session, rows: first..<(last + 1), column: session.trackID.flatMap { columnOf[$0] })
        }
        .sorted { ($0.rows.lowerBound, $0.column ?? -1) < ($1.rows.lowerBound, $1.column ?? -1) }
        let cells = cells
        empty = timetable.tracks.isEmpty ? [] : rows.indices.flatMap { row in
            (0..<timetable.tracks.count).compactMap { column in
                cells.contains { $0.rows.contains(row) && ($0.column == nil || $0.column == column) } ? nil : EmptySlot(row: row, column: column)
            }
        }
    }
}

/// 나만의 시간표 (contract "트랙·세션 시간표" 2026-10-26 `찜한 세션`): the favorited sessions in time order on one line, with the free time and
/// the move to another room between them and a warning band before sessions favorited at the same time.
enum MyTimetable {
    struct Item: Equatable {
        let session: TimetableSession
        let track: TimetableTrack?
    }
    enum Entry: Equatable {
        case session(Item)
        /// Sessions favorited at the same time follow (`count` of them).
        case overlap(count: Int)
        /// Free time before the next session (0 when it starts right away) and where to go when it is in another track or room.
        case gap(minutes: Int, moveTo: TimetableTrack?)
    }
    static func entries(_ items: [Item]) -> [Entry] {
        var clusters: [[Item]] = []
        for item in items.sorted(by: { ($0.session.start, $0.session.end) < ($1.session.start, $1.session.end) }) {
            if let last = clusters.last, let end = last.map(\.session.end).max(), item.session.start < end {
                clusters[clusters.count - 1].append(item)
            } else {
                clusters.append([item])
            }
        }
        var entries: [Entry] = []
        for (index, cluster) in clusters.enumerated() {
            if index > 0, let previous = clusters[index - 1].last, let end = clusters[index - 1].map(\.session.end).max(), let next = cluster.first {
                let minutes = Int(next.session.start.timeIntervalSince(end) / 60)
                let moved = next.track != nil && next.track != previous.track
                if minutes > 0 || moved { entries.append(.gap(minutes: minutes, moveTo: moved ? next.track : nil)) }
            }
            if cluster.count > 1 { entries.append(.overlap(count: cluster.count)) }
            entries += cluster.map(Entry.session)
        }
        return entries
    }
}
