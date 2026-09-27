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
                    Text(schedule.title).font(.headline)
                    Text(schedule.dateLabel.isEmpty ? "일정 미확인" : schedule.dateLabel)
                    Text("시작: \(ActivityText.date(schedule.startAt, timeZone: schedule.timeZone))").font(.footnote)
                    Text("종료: \(ActivityText.date(schedule.endAt, timeZone: schedule.timeZone))").font(.footnote)
                    if !schedule.timeZone.isEmpty { Text(schedule.timeZone).font(.caption).foregroundStyle(.secondary) }
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
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = timeZone.flatMap(TimeZone.init(identifier:)) ?? .current
        formatter.dateFormat = "yyyy년 M월 d일 a h:mm zzz"
        return formatter.string(from: date)
    }
}
