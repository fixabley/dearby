import SwiftUI

/// `행사 일정`: one row per schedule of the track (모집 or 모집 마감, `schedules[]`, the activity period) with its date text and, for a dated
/// one, `캘린더에 추가` (contract "내 캘린더에 추가" 2026-10-22), then the overlap check. Input and callbacks only (pure-ui.json).
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
    let rows: [Row]
    /// At least one schedule can be compared with the device calendar (timed, or date-only with an admin time window).
    let canCheckCalendar: Bool
    let checkCalendar: () -> Void
    let addToCalendar: (CalendarEventDraft) -> Void
    /// A row with a timetable was pressed.
    let openTimetable: (Row) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Divider()
            Text("행사 일정").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.teal)
            if rows.isEmpty { Text("일정 미확인").font(.dearby(.subheadline)) }
            VStack(spacing: 0) {
                ForEach(rows.indices, id: \.self) { index in
                    let row = rows[index]
                    HStack(alignment: .top, spacing: 14) {
                        VStack(spacing: 0) {
                            Circle().fill(DearbyStyle.teal).frame(width: 7, height: 7)
                            Rectangle().fill(index < rows.count - 1 ? DearbyStyle.line : .clear).frame(width: 1)
                        }.padding(.top, 6)
                        if row.hasTimetable {
                            // The whole row opens the block's timetable sheet (contract "트랙·세션 시간표" 2026-10-26 모달 시트), a list row with
                            // `시간표` and a chevron at the end; `캘린더에 추가` stays its own button.
                            Button { openTimetable(row) } label: {
                                HStack(alignment: .center, spacing: 8) {
                                    information(row)
                                    DearbyMiniBadge(title: "시간표", systemImage: "tablecells")
                                    Image(systemName: "chevron.right").font(.dearby(.footnote).weight(.semibold)).foregroundStyle(.tertiary)
                                }
                                .padding(.bottom, 18).contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel([row.title, row.date, row.badge, "시간표"].compactMap { $0 }.joined(separator: ", "))
                            .accessibilityHint("누르면 시간표를 보여요")
                            .accessibilityIdentifier("timetable-\(row.id)")
                        } else {
                            information(row).padding(.bottom, 18)
                        }
                        if let draft = row.draft {
                            Button("캘린더에 추가", systemImage: "calendar.badge.plus") { addToCalendar(draft) }
                                .labelStyle(.iconOnly).buttonStyle(.borderless).font(.dearby(.title3)).frame(minWidth: 44, minHeight: 44)
                                .accessibilityLabel("\(row.title) 캘린더에 추가")
                        }
                    }.fixedSize(horizontal: false, vertical: true)
                }
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
    private func information(_ row: Row) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(row.title).font(.dearby(.subheadline).weight(.semibold))
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(row.date).font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
                if let badge = row.badge { DearbyMiniBadge(title: badge, filled: true) }
            }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
