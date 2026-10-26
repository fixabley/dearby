import SwiftUI

/// `선발 과정` and `활동 일정` (contract "선발 과정·활동 일정 분리" 2026-10-26, replacing the single `행사 일정`): one row per schedule of the
/// track (모집 or 모집 마감, `schedules[]`, the activity period) with its date text and, for a dated one, `캘린더에 추가` (contract "내 캘린더에
/// 추가" 2026-10-22); an empty section is left out; the overlap check follows both. 활동 일정 folds a day's blocks under one date row (contract
/// "하루 시간표" 2026-10-26): collapsed by default, pressed it unfolds `시간표 보기`·`캘린더에 추가` and the block rows. Input and callbacks only
/// (pure-ui.json); which days are unfolded is the view's own state.
struct ActivityInformationView: View {
    struct Row: Identifiable {
        let id: String
        let title: String
        let date: String
        /// nil for a date-less schedule: no `캘린더에 추가`.
        let draft: CalendarEventDraft?
        /// The block has a 트랙·세션 시간표 (contract "트랙·세션 시간표" 2026-10-26): the row opens it in a sheet.
        var hasTimetable = false
        /// `D-3`/`D-DAY` beside the date (the recruitment row while recruiting, contract "모집 날짜 표기 — 2026-10-26").
        var badge: String?
    }
    /// A Seoul day with two or more timed blocks (contract "하루 시간표").
    struct Day: Identifiable {
        let id: String
        /// `10월 28일 (수)`.
        let title: String
        /// `10:00 ~ 17:30 · 블록 4개`.
        let date: String
        /// `캘린더에 추가` over the whole day.
        let draft: CalendarEventDraft?
        /// Some block has a timetable: `시간표 보기` shows the day as one table.
        let hasTimetable: Bool
        /// The blocks in start order.
        let rows: [Row]
    }
    enum Entry: Identifiable {
        case row(Row)
        case day(Day)
        var id: String {
            switch self {
            case .row(let row): row.id
            case .day(let day): day.id
            }
        }
    }
    /// `선발 과정`.
    let selection: [Row]
    /// `활동 일정`.
    let activity: [Entry]
    /// At least one schedule can be compared with the device calendar (timed, or date-only with an admin time window).
    let canCheckCalendar: Bool
    let checkCalendar: () -> Void
    let addToCalendar: (CalendarEventDraft) -> Void
    /// A row with a timetable was pressed.
    let openTimetable: (Row) -> Void
    /// A day's `시간표 보기` was pressed.
    let openDayTimetable: (Day) -> Void
    @State private var unfolded: Set<String> = []
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Divider()
            if selection.isEmpty && activity.isEmpty {
                heading("활동 일정")
                Text("일정 미확인").font(.dearby(.subheadline))
            }
            if !selection.isEmpty {
                heading("선발 과정")
                timeline(selection.map(Entry.row))
            }
            if !activity.isEmpty {
                heading("활동 일정")
                timeline(activity)
            }
            // Contract "겹침 확인 시간" 2026-10-22: the button whenever one schedule can be compared, else one line saying why not.
            if canCheckCalendar {
                Button(action: checkCalendar) { Text("겹치는 시간 확인하기").frame(maxWidth: .infinity) }.buttonStyle(.bordered).controlSize(.large).font(.dearby(.body).weight(.semibold))
                    .accessibilityIdentifier("check-calendar")
                Text("기기 캘린더와 이 기기 안에서만 비교해요.").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                    .frame(maxWidth: .infinity)
            } else {
                Text("이 활동은 시간이 정해진 일정이 없어 겹침을 확인할 수 없어요").font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
            }
        }
    }
    /// A section title read as a heading (contract: 섹션 머리글은 heading으로 읽힌다).
    private func heading(_ title: String) -> some View {
        Text(title).font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.teal).accessibilityAddTraits(.isHeader)
    }
    /// One section's entries on a dot timeline whose connecting line ends at the section's last entry.
    private func timeline(_ entries: [Entry]) -> some View {
        VStack(spacing: 0) {
            ForEach(entries.indices, id: \.self) { index in
                HStack(alignment: .top, spacing: 14) {
                    VStack(spacing: 0) {
                        Circle().fill(DearbyStyle.teal).frame(width: 7, height: 7)
                        Rectangle().fill(index < entries.count - 1 ? DearbyStyle.line : .clear).frame(width: 1)
                    }.padding(.top, 6)
                    switch entries[index] {
                    case .row(let row): rowContent(row)
                    case .day(let day): dayContent(day)
                    }
                }
                .padding(.bottom, 18).fixedSize(horizontal: false, vertical: true)
            }
        }
    }
    /// A block row: the whole row opens the block's timetable sheet when it has one (contract "트랙·세션 시간표" 2026-10-26 모달 시트), a list
    /// row with `시간표` and a chevron at the end; `캘린더에 추가` stays its own button.
    private func rowContent(_ row: Row) -> some View {
        HStack(alignment: .top, spacing: 14) {
            if row.hasTimetable {
                Button { openTimetable(row) } label: {
                    HStack(alignment: .center, spacing: 8) {
                        information(row.title, row.date, badge: row.badge)
                        DearbyMiniBadge(title: "시간표", systemImage: "tablecells")
                        Image(systemName: "chevron.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(.tertiary)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel([row.title, row.date, row.badge, "시간표"].compactMap { $0 }.joined(separator: ", "))
                .accessibilityHint("누르면 시간표를 보여요")
                .accessibilityIdentifier("timetable-\(row.id)")
            } else {
                information(row.title, row.date, badge: row.badge)
            }
            if let draft = row.draft { calendarButton(row.title, draft) }
        }
    }
    /// A day row (contract "하루 시간표"): the date, its range and block count, folded; unfolded, `시간표 보기` (when some block has one) and
    /// `캘린더에 추가` over the day, then the block rows as they are on their own.
    private func dayContent(_ day: Day) -> some View {
        let open = unfolded.contains(day.id)
        return VStack(alignment: .leading, spacing: 0) {
            Button {
                withAnimation(.snappy) { if open { unfolded.remove(day.id) } else { unfolded.insert(day.id) } }
            } label: {
                HStack(alignment: .center, spacing: 8) {
                    information(day.title, day.date, badge: nil)
                    if day.hasTimetable { DearbyMiniBadge(title: "시간표", systemImage: "tablecells") }
                    Image(systemName: "chevron.down").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(.tertiary)
                        .rotationEffect(.degrees(open ? 180 : 0))
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel([day.title, day.date, day.hasTimetable ? "시간표" : nil].compactMap { $0 }.joined(separator: ", "))
            .accessibilityValue(open ? "펼침" : "접힘").accessibilityHint("누르면 이 날의 일정을 펼치거나 접어요")
            .accessibilityIdentifier("day-\(day.id)")
            if open {
                HStack(spacing: 8) {
                    if day.hasTimetable {
                        Button("시간표 보기", systemImage: "tablecells") { openDayTimetable(day) }.accessibilityIdentifier("day-timetable-\(day.id)")
                    }
                    if let draft = day.draft { Button("캘린더에 추가", systemImage: "calendar.badge.plus") { addToCalendar(draft) } }
                }
                .buttonStyle(.bordered).controlSize(.small).font(.dearby(.footnote).weight(.semibold)).padding(.top, 10)
                VStack(spacing: 12) {
                    ForEach(day.rows) { rowContent($0) }
                }.padding(.top, 14)
            }
        }
    }
    private func calendarButton(_ title: String, _ draft: CalendarEventDraft) -> some View {
        Button("캘린더에 추가", systemImage: "calendar.badge.plus") { addToCalendar(draft) }
            .labelStyle(.iconOnly).buttonStyle(.borderless).font(.dearby(.title3)).frame(minWidth: 44, minHeight: 44)
            .accessibilityLabel("\(title) 캘린더에 추가")
    }
    private func information(_ title: String, _ date: String, badge: String?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.dearby(.subheadline).weight(.semibold))
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(date).font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
                if let badge { DearbyMiniBadge(title: badge, filled: true) }
            }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
