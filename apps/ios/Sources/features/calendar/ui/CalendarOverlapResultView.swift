import SwiftUI

/// 기기 캘린더와 활동 일정이 겹치는 한 쌍. 일정 제목·시각은 화면 표시에만 쓰고 저장하지 않는다.
struct CalendarOverlapItem: Identifiable {
    let id: String
    /// 활동 일정 이름(예: '본 행사').
    let sessionTitle: String
    let sessionStart: Date
    let sessionEnd: Date
    /// 기기 일정 제목. 이 기기에서만 보인다.
    let eventTitle: String
    let eventStart: Date
    let eventEnd: Date
}

/// 겹침 확인 결과의 상태. 계산·권한 요청은 화면이 맡고 이 값만 넘긴다.
enum CalendarOverlapDisplay {
    case checking
    case denied
    case clear
    case overlaps([CalendarOverlapItem])
}

/// 기기 캘린더 겹침 확인 결과. 확인 중·권한 거부(설정 열기)·겹침 없음·겹치는 일정 목록을 보인다.
struct CalendarOverlapResultView: View {
    let display: CalendarOverlapDisplay
    /// 활동 일정의 시간대. 시각을 이 시간대로 보인다.
    var timeZone: TimeZone = .current
    var openSettings: () -> Void = {}
    let close: () -> Void
    var body: some View {
        VStack(spacing: 0) {
            DearbySheetHeader(title: "겹치는 시간 확인", close: close)
            ScrollView {
                VStack(alignment: .leading, spacing: 16) { content }
                    .frame(maxWidth: .infinity, alignment: .leading).padding(20)
            }
            Label("캘린더 일정은 이 기기에서만 비교하고 저장하거나 보내지 않아요.", systemImage: "lock")
                .font(.dearby(.caption)).foregroundStyle(DearbyStyle.quiet)
                .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 20).padding(.bottom, 16)
        }
    }
    @ViewBuilder private var content: some View {
        switch display {
        case .checking:
            ProgressView("캘린더와 비교하는 중이에요").font(.dearby(.subheadline)).tint(DearbyStyle.teal)
                .frame(maxWidth: .infinity).padding(.vertical, 48)
        case .denied:
            state(icon: "calendar.badge.exclamationmark", title: "캘린더 접근이 꺼져 있어요",
                  message: "겹치는 일정을 확인하려면 설정에서 캘린더 접근을 허용해 주세요. 허용하지 않아도 신청은 그대로 할 수 있어요.") {
                Button("설정 열기", action: openSettings).buttonStyle(DearbyButtonStyle(outlined: true))
            }
        case .clear:
            state(icon: "checkmark.circle", title: "겹치는 일정이 없어요", message: "활동 시간에 캘린더 일정이 없어요.") { EmptyView() }
        case .overlaps(let items):
            Text("\(items.count)개 일정이 겹쳐요").font(.dearby(.title3).bold()).foregroundStyle(DearbyStyle.ink)
                .accessibilityAddTraits(.isHeader)
            ForEach(items) { row($0) }
        }
    }
    private func state(icon: String, title: String, message: String, @ViewBuilder action: () -> some View) -> some View {
        VStack(spacing: 12) {
            Image(systemName: icon).font(.dearby(.title)).foregroundStyle(DearbyStyle.teal)
                .frame(width: 68, height: 68).background(DearbyStyle.mint, in: Circle()).accessibilityHidden(true)
            Text(title).font(.dearby(.title3).bold()).foregroundStyle(DearbyStyle.ink).multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text(message).font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet).multilineTextAlignment(.center)
            action()
        }.frame(maxWidth: .infinity).padding(.vertical, 32)
    }
    private func row(_ item: CalendarOverlapItem) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text(day(item.sessionStart)).font(.dearby(.caption).weight(.semibold)).foregroundStyle(DearbyStyle.quiet)
                Text("겹침").font(.dearby(.caption).weight(.bold)).foregroundStyle(Self.overlapText)
                    .padding(.horizontal, 8).padding(.vertical, 2).background(Self.overlapFill, in: Capsule())
            }
            Text(item.eventTitle).font(.dearby(.headline)).foregroundStyle(DearbyStyle.ink)
            Label(range(item.eventStart, item.eventEnd), systemImage: "calendar")
                .font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.ink)
            Text("활동 일정 · \(item.sessionTitle) \(range(item.sessionStart, item.sessionEnd))")
                .font(.dearby(.subheadline)).foregroundStyle(DearbyStyle.quiet)
        }.padding(14).frame(maxWidth: .infinity, alignment: .leading)
            .background(.white, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(DearbyStyle.line))
            .accessibilityElement(children: .combine)
    }
    private func formatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = timeZone
        formatter.dateFormat = format
        return formatter
    }
    private func day(_ date: Date) -> String { formatter("M월 d일 (E)").string(from: date) }
    private func range(_ start: Date, _ end: Date) -> String {
        let time = formatter("HH:mm")
        return "\(time.string(from: start))–\(time.string(from: end))"
    }
    // 주황 계열 겹침 표시. 글자 #9D5109 / 배경 #FFF1E0 대비 약 5.5:1.
    private static let overlapText = Color(red: 157.0 / 255, green: 81.0 / 255, blue: 9.0 / 255)
    private static let overlapFill = Color(red: 1, green: 241.0 / 255, blue: 224.0 / 255)
}

#if DEBUG
/// 미리보기와 캡처 테스트가 함께 쓰는 예시. 일정 제목은 실제가 아닌 예시다.
enum CalendarOverlapSample {
    static let seoul = TimeZone(identifier: "Asia/Seoul") ?? .current
    static func date(_ text: String) -> Date { ISO8601DateFormatter().date(from: text) ?? .now }
    static let items = [
        CalendarOverlapItem(id: "1", sessionTitle: "본 행사", sessionStart: date("2026-10-24T04:00:00Z"), sessionEnd: date("2026-10-24T08:00:00Z"),
                            eventTitle: "팀 주간 회의", eventStart: date("2026-10-24T05:00:00Z"), eventEnd: date("2026-10-24T06:00:00Z")),
        CalendarOverlapItem(id: "2", sessionTitle: "본 행사", sessionStart: date("2026-10-24T04:00:00Z"), sessionEnd: date("2026-10-24T08:00:00Z"),
                            eventTitle: "치과 예약", eventStart: date("2026-10-24T07:30:00Z"), eventEnd: date("2026-10-24T08:30:00Z"))
    ]
}

#Preview("겹침") { CalendarOverlapResultView(display: .overlaps(CalendarOverlapSample.items), timeZone: CalendarOverlapSample.seoul) {} }
#Preview("권한 거부") { CalendarOverlapResultView(display: .denied) {} }
#Preview("겹침 없음") { CalendarOverlapResultView(display: .clear) {} }
#endif
