import Foundation

@main
struct DetailPresentationTests {
    static func main() {
        func period(_ start: String? = nil, _ startDay: String? = nil, _ end: String? = nil, _ endDay: String? = nil, zone: String? = "Asia/Seoul") -> EventPeriodPresentation {
            EventPeriodPresentation(startsAt: start, startsOn: startDay, endsAt: end, endsOn: endDay, timezone: zone)
        }
        let same = period("2026-09-15T14:00:00+09:00", "2026-09-15", "2026-09-15T16:00:00+09:00")
        precondition(same.lines == [.init(label: nil, date: "2026년 9월 15일 (화)", time: "오후 2시부터 오후 4시까지")])
        precondition(same.note == "한국 시간")
        let across = period("2026-12-31T23:00:00+09:00", nil, "2027-01-01T01:00:00+09:00")
        precondition(across.lines.map(\.label) == ["시작", "종료"])
        precondition(across.lines.map(\.date) == ["2026년 12월 31일 (목)", "2027년 1월 1일 (금)"])
        precondition(across.lines.map(\.time) == ["오후 11시부터", "오전 1시까지"])
        let days = period(nil, "2026-10-14", nil, "2026-10-14")
        precondition(days.lines.count == 1 && days.lines[0].time == "시간 미확인")
        let mixed = period(nil, "2026-09-15", "2026-09-15T16:00:00+09:00")
        precondition(mixed.lines[0].time == "시작 시간 미확인 · 종료 오후 4시")
        precondition(period(nil, "2026-10-14").note!.contains("종료 미확인"))
        precondition(period(nil, nil, nil, "2026-10-14").lines[0].label == "마감")
        precondition(period("2026-09-15T14:00:12+09:00").lines[0].time == "오후 2시 0분 12초")
        for bad in ["2026-02-30T14:00:00+09:00", "2026-09-15T24:00:00+09:00", "not-a-date", "2026-09-15T14:00:00.123+09:00"] {
            let value = period(bad)
            precondition(value.lines[0].date == bad && value.lines[0].time.contains("확인 필요"))
        }
        precondition(period("2026-09-15T14:00:00+09:00", "2026-09-16").lines[0].time.contains("일치 확인"))
        precondition(period(nil, "2026-09-16", nil, "2026-09-15").lines[0].time == "기간 순서 확인 필요")
        precondition(period(nil, "2026-09-15", zone: "Bad/Zone").lines[0].time == "시간대 확인 필요")
        precondition(period("2026-09-15T14:00:00+09:00", zone: nil).lines[0].date.contains("+09:00"))
        precondition(period().lines[0].date == "일정 미확인")
        let room = NoticePlaceState(name: "충북대학교 중앙도서관 2관 세미나실(5층)", address: "충북 청주시 서원구")
        precondition(room.name == "충북대학교 중앙도서관 2관 세미나실" && room.detail == "5층\n충북 청주시 서원구")
        let rooms = NoticePlaceState(name: "중앙도서관 상담실 1호실·6호실", address: nil)
        precondition(rooms.name == "중앙도서관 상담실" && rooms.detail == "1호실·6호실")
        let building = NoticePlaceState(name: "인문대학 본관(N16-1) 459호", address: nil)
        precondition(building.name == "인문대학 본관(N16-1)" && building.detail == "459호")
        precondition(NoticePlaceState(name: "행사장(예정)", address: nil).name == "행사장(예정)")
        precondition(NoticePlaceState(name: "행사장", address: "행사장").detail == nil)
        precondition(NoticePlaceState.safeOnlineURL("https://example.com/path?q=1")?.absoluteString == "https://example.com/path?q=1")
        for bad in [nil, "javascript:alert(1)", "https://", "https://user:pass@example.com", "https://example.com/a b"] as [String?] {
            precondition(NoticePlaceState.safeOnlineURL(bad) == nil)
        }
        print("PASS: detail date/time hierarchy, same-day/multiday/year/mixed precision/seconds/unknown/conflicting/invalid/reversed; lossless explicit room separation, address deduplication, safe online URL")
    }
}
