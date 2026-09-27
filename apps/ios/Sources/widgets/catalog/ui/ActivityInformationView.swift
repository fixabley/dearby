import SwiftUI

struct ActivityInformationView: View {
    let activity: ActivityModel
    var body: some View {
        Section("일정") {
            field("활동 일정", activity.dateLabel)
            field("모집 시작", ActivityText.date(activity.recruitmentStartAt))
            field("모집 마감", ActivityText.date(activity.recruitmentEndAt))
            ForEach(activity.schedules) { schedule in
                VStack(alignment: .leading, spacing: 6) {
                    if schedule.title != activity.title { Text(schedule.title).font(.headline) }
                    if schedule.dateLabel != activity.dateLabel {
                        Text(schedule.dateLabel.isEmpty ? "일정 미확인" : schedule.dateLabel)
                    }
                    if schedule.startAt == nil && schedule.endAt == nil {
                        Text("시간 미정").font(.footnote).foregroundStyle(.secondary)
                    } else {
                        Text(schedule.startAt.map { "시작: " + ActivityText.date($0, timeZone: schedule.timeZone) }
                            ?? "시작 시각 미정").font(.footnote)
                        Text(schedule.endAt.map { "종료: " + ActivityText.date($0, timeZone: schedule.timeZone) }
                            ?? "종료 시각 미정").font(.footnote)
                    }
                }
            }
            Text("기기 캘린더와의 일정 비교는 아직 제공하지 않아요.").font(.caption).foregroundStyle(.secondary)
        }
        Section("참가 안내") {
            field("대상", activity.audience)
            field("지원 조건", activity.qualification)
            field("모집 직군", activity.roles.isEmpty ? nil : activity.roles.joined(separator: ", "))
            field("비용", activity.cost)
            field("장소", activity.location)
        }
    }
    private func field(_ title: String, _ value: String?) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value.flatMap { $0.isEmpty ? nil : $0 } ?? "미확인")
        }.padding(.vertical, 2)
    }
}

// Display formatting stays in the owning widget; wire timestamps remain unchanged.
enum ActivityText {
    static func date(_ raw: String?, timeZone: String? = nil) -> String {
        guard let date = CatalogModel.date(raw) else { return "미확인" }
        return self.date(date, timeZone: timeZone)
    }
    static func date(_ date: Date, timeZone: String? = nil) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = timeZone.flatMap(TimeZone.init(identifier:)) ?? .current
        formatter.dateFormat = "yyyy년 M월 d일 a h:mm zzz"
        return formatter.string(from: date)
    }
}
