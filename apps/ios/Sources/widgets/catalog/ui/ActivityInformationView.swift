import SwiftUI

struct ActivityInformationView: View {
    let activity: ActivityModel
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let checkCalendar: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Divider()
            Text("행사 일정").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            Text(activity.dateLabel.isEmpty ? "일정 미확인" : activity.dateLabel).font(.subheadline)
            if activity.schedules.isEmpty {
                Text("세부 시간은 아직 확인되지 않았어요.").font(.subheadline).foregroundStyle(DearbyStyle.quiet)
            }
            VStack(spacing: 0) {
                ForEach(activity.schedules) { schedule in
                    scheduleRow(schedule)
                }
            }
            Button("겹치는 시간 확인하기", action: checkCalendar).buttonStyle(DearbyButtonStyle(outlined: true))
            Text("선택한 기기 캘린더 일정과 비교해요.").font(.caption).foregroundStyle(DearbyStyle.quiet)
                .frame(maxWidth: .infinity)
        }
        VStack(alignment: .leading, spacing: 20) {
            Divider()
            Text("참가 안내").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            field("참가비", activity.cost)
            field("등록 방법", activity.participationType == .selection ? "신청 후 주최 측 선정" : "공식 사이트에서 참가 등록")
            field("지원 조건", activity.qualification)
            if !activity.roles.isEmpty { field("관심 분야", activity.roles.joined(separator: " · ")) }
            Divider().padding(.top, 4)
            Text("장소").font(.title2.bold()).foregroundStyle(DearbyStyle.teal)
            Text(activity.location ?? "장소 미확인").font(.subheadline)
        }
    }
    private func scheduleRow(_ schedule: ActivityScheduleModel) -> some View {
        let layout = dynamicTypeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
            : AnyLayout(HStackLayout(alignment: .top, spacing: 12))
        return layout {
            VStack(alignment: .leading, spacing: 4) {
                Text(ActivityText.clock(schedule.startAt, timeZone: schedule.timeZone))
                Text("– " + ActivityText.clock(schedule.endAt, timeZone: schedule.timeZone))
            }.font(.subheadline).foregroundStyle(DearbyStyle.quiet).frame(minWidth: 90, alignment: .leading)
            VStack(spacing: 0) {
                Circle().fill(DearbyStyle.teal).frame(width: 7, height: 7)
                Rectangle().fill(DearbyStyle.line).frame(width: 1).frame(minHeight: 44)
            }.padding(.top, 6).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 5) {
                Text(schedule.title).font(.subheadline)
                if schedule.dateLabel != activity.dateLabel { Text(schedule.dateLabel).font(.caption).foregroundStyle(DearbyStyle.quiet) }
                Text(ActivityText.day(schedule.startAt, timeZone: schedule.timeZone) + " · " + schedule.timeZone).font(.caption2).foregroundStyle(DearbyStyle.quiet)
            }.frame(maxWidth: .infinity, alignment: .leading)
        }.padding(.bottom, 10)
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

// Display formatting stays in the owning widget; wire timestamps remain unchanged.
enum ActivityText {
    static func title(_ activity: ActivityModel) -> String {
        let prefix = "[목 데이터] "
        if activity.isPreview == true && activity.title.hasPrefix(prefix) {
            return String(activity.title.dropFirst(prefix.count))
        }
        return activity.title
    }
    static func day(_ raw: String?, timeZone: String) -> String {
        guard let date = CatalogModel.date(raw) else { return "날짜 미확인" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: timeZone)
        formatter.dateFormat = "M월 d일"
        return formatter.string(from: date)
    }
    static func clock(_ raw: String?, timeZone: String) -> String {
        guard let date = CatalogModel.date(raw) else { return "시간 미정" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: timeZone)
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }
    static func shortDate(_ raw: String?) -> String {
        guard let date = CatalogModel.date(raw) else { return "미확인" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "M.dd HH:mm"
        return formatter.string(from: date)
    }
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
