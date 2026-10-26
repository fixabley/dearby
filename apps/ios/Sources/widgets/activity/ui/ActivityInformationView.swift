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
        /// The block has a 트랙·세션 시간표 (contract "트랙·세션 시간표" 2026-10-26): `{제목} 시간표` under the date.
        var hasTimetable = false
        /// `D-3`/`D-DAY` beside the date (the recruitment row while recruiting, contract "모집 날짜 표기 — 2026-10-26").
        var badge: String?
    }
    let rows: [Row]
    /// At least one schedule can be compared with the device calendar (timed, or date-only with an admin time window).
    let canCheckCalendar: Bool
    let checkCalendar: () -> Void
    let addToCalendar: (CalendarEventDraft) -> Void
    /// `{제목} 시간표` on a row with a timetable.
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
                        VStack(alignment: .leading, spacing: 2) {
                            Text(row.title).font(.dearby(.subheadline).weight(.semibold))
                            HStack(alignment: .firstTextBaseline, spacing: 6) {
                                Text(row.date).font(.dearby(.footnote)).foregroundStyle(DearbyStyle.quiet)
                                if let badge = row.badge { DearbyMiniBadge(title: badge, filled: true) }
                            }
                            if row.hasTimetable {
                                Button("\(row.title) 시간표", systemImage: "tablecells") { openTimetable(row) }
                                    .buttonStyle(.bordered).controlSize(.small).font(.dearby(.footnote).weight(.semibold)).padding(.top, 6)
                                    .accessibilityIdentifier("timetable-\(row.id)")
                            }
                        }.padding(.bottom, 18).frame(maxWidth: .infinity, alignment: .leading)
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
}
