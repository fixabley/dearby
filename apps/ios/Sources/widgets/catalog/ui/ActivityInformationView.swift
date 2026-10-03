import SwiftUI

struct ActivityInformationView: View {
    let activity: ActivityModel
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Divider()
            Text("행사 일정").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            field("활동 일정", activity.dateLabel)
            field("모집 상태", activity.demoStatus)
            ForEach(activity.schedules) { schedule in
                VStack(alignment: .leading, spacing: 6) {
                    if schedule.title != activity.title { Text(schedule.title).font(.headline) }
                    if schedule.dateLabel != activity.dateLabel {
                        Text(schedule.dateLabel.isEmpty ? "일정 미확인" : schedule.dateLabel)
                    }
                    Text("시작: " + ActivityText.date(schedule.start, timeZone: schedule.timeZone)).font(.footnote)
                    Text("종료: " + ActivityText.date(schedule.end, timeZone: schedule.timeZone)).font(.footnote)
                }
            }
            Text("예시 바쁜 시간과 비교할 수 있어요. 실제 캘린더에 접근하지 않아요.").font(.caption).foregroundStyle(DearbyStyle.quiet)
        }
        VStack(alignment: .leading, spacing: 16) {
            Divider()
            Text("참가 안내").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            field("대상", activity.audience)
            field("모집 직군", activity.roles.isEmpty ? nil : activity.roles.joined(separator: ", "))
            field("비용", activity.cost)
            Divider()
            Text("장소").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            Text(activity.location)
        }
    }
    private func field(_ title: String, _ value: String?) -> some View {
        let layout = dynamicTypeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 6))
            : AnyLayout(HStackLayout(alignment: .top, spacing: 16))
        return layout {
            Text(title).font(.subheadline).foregroundStyle(DearbyStyle.quiet)
                .frame(minWidth: 76, alignment: .leading)
            Text(value.flatMap { $0.isEmpty ? nil : $0 } ?? "미확인").font(.subheadline)
                .frame(maxWidth: .infinity, alignment: .leading)
        }.padding(.vertical, 2)
    }
}

// Keep the activity time zone explicit, regardless of the device locale.
enum ActivityText {
    static func date(_ date: Date, timeZone: String? = nil) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = timeZone.flatMap(TimeZone.init(identifier:)) ?? .current
        formatter.dateFormat = "yyyy년 M월 d일 a h:mm zzz"
        return formatter.string(from: date)
    }
}
