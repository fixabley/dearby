import SwiftUI

struct ActivityInformationView: View {
    let activity: ActivityModel
    let checkCalendar: () -> Void
    private var sessions: [(time: String, title: String)] {
        // Several days: prefix the date so sessions are not read as one day (web rule).
        let days = Set(activity.schedules.map { ActivityText.day($0.start, zone: $0.timeZone) })
        return activity.schedules.map { schedule in
            let range = ActivityText.time(schedule.start, zone: schedule.timeZone) + " – " + ActivityText.time(schedule.end, zone: schedule.timeZone)
            return ((days.count > 1 ? ActivityText.day(schedule.start, zone: schedule.timeZone) + " " : "") + range, schedule.title)
        }
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Divider()
            Text("행사 일정").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.teal)
            Text(activity.dateLabel.isEmpty ? "일정 미확인" : activity.dateLabel).font(.dearby(.subheadline))
            VStack(spacing: 0) {
                ForEach(sessions.indices, id: \.self) { index in
                    let session = sessions[index]
                    HStack(alignment: .top, spacing: 14) {
                        Text(session.time)
                            .font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet).frame(width: 92, alignment: .leading)
                        VStack(spacing: 0) {
                            Circle().fill(DearbyStyle.teal).frame(width: 7, height: 7)
                            Rectangle().fill(index < sessions.count - 1 ? DearbyStyle.line : .clear).frame(width: 1)
                        }.padding(.top, 5)
                        Text(session.title).font(.dearby(.subheadline)).padding(.bottom, 18).frame(maxWidth: .infinity, alignment: .leading)
                    }.fixedSize(horizontal: false, vertical: true)
                }
            }
            // The overlap check stays an example and needs timed schedules.
            if !sessions.isEmpty {
                Button("겹치는 시간 확인하기", action: checkCalendar).buttonStyle(DearbyButtonStyle(outlined: true))
                Text("예시 캘린더 일정과 비교해요.").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                    .frame(maxWidth: .infinity)
            }
        }
        VStack(alignment: .leading, spacing: 16) {
            Divider()
            Text("참가 안내").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.teal)
            DearbyInfoRow(title: "참가비", value: activity.cost ?? "미확인")
            DearbyInfoRow(title: "참여 방식", value: activity.participationType == .selection ? "선발형" : "바로 신청")
            DearbyInfoRow(title: "역할", value: activity.roles.isEmpty ? "미확인" : activity.roles.joined(separator: ", "))
            Divider()
            Text("장소").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.teal)
            Text(activity.location ?? "장소 미확인")
        }
    }
}
enum ActivityText {
    static func checked(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "M월 d일 HH:mm (한국 시간)"
        return formatter.string(from: date)
    }
    static func time(_ date: Date, zone: String = "Asia/Seoul") -> String { format(date, "HH:mm", zone) }
    static func day(_ date: Date, zone: String) -> String { format(date, "M/d", zone) }
    private static func format(_ date: Date, _ pattern: String, _ zone: String) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: zone) ?? TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = pattern
        return formatter.string(from: date)
    }
}
