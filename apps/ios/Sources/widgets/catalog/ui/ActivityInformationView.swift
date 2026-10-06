import SwiftUI

struct ActivityInformationView: View {
    let activity: ActivityModel
    let checkCalendar: () -> Void
    private var sessions: [(start: Date, end: Date, title: String)] {
        guard let schedule = activity.schedules.first else { return [] }
        guard activity.id == "conference" else { return [(schedule.start, schedule.end, "활동 진행")] }
        let agenda = [(0, 30, "등록 및 오프닝"), (30, 90, "개발 세션"), (90, 120, "휴식"),
                      (120, 180, "디자인 세션"), (180, 240, "네트워킹")]
        return agenda.map { (schedule.start.addingTimeInterval(Double($0.0) * 60),
                             schedule.start.addingTimeInterval(Double($0.1) * 60), $0.2) }
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Divider()
            Text("행사 일정").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.teal)
            Text(activity.dateLabel).font(.dearby(.subheadline))
            VStack(spacing: 0) {
                ForEach(sessions.indices, id: \.self) { index in
                    let session = sessions[index]
                    HStack(alignment: .top, spacing: 14) {
                        Text(ActivityText.time(session.start) + " – " + ActivityText.time(session.end))
                            .font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet).frame(width: 92, alignment: .leading)
                        VStack(spacing: 0) {
                            Circle().fill(DearbyStyle.teal).frame(width: 7, height: 7)
                            Rectangle().fill(index < sessions.count - 1 ? DearbyStyle.line : .clear).frame(width: 1)
                        }.padding(.top, 5)
                        Text(session.title).font(.dearby(.subheadline)).padding(.bottom, 18).frame(maxWidth: .infinity, alignment: .leading)
                    }.fixedSize(horizontal: false, vertical: true)
                }
            }
            Button("겹치는 시간 확인하기", action: checkCalendar).buttonStyle(DearbyButtonStyle(outlined: true))
            Text("예시 캘린더 일정과 비교해요.").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                .frame(maxWidth: .infinity)
        }
        VStack(alignment: .leading, spacing: 16) {
            Divider()
            Text("참가 안내").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.teal)
            DearbyInfoRow(title: "참가비", value: activity.cost)
            DearbyInfoRow(title: "등록 방법", value: "신청 화면에서 흐름 체험")
            DearbyInfoRow(title: "준비물", value: "별도 준비물 없음")
            DearbyInfoRow(title: "세부 일정", value: "디자인 확인용 예시예요.")
            Divider()
            Text("장소").font(.dearby(.title2).bold()).foregroundStyle(DearbyStyle.teal)
            Text(activity.location)
            Text("실제 예약·접수와 무관한 예시입니다.").font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
        }
    }
}
enum ActivityText {
    static func time(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }
}
