import Foundation

/// A link the app was opened with (contract "유니버설 링크·App Links·활동 공유·오픈그래프" 2026-10-22): `dearby://cards/…` from the web
/// card page's "Dearby 앱에 저장", or a universal link on the build's web origin for `/cards/*` and `/activities/*`. Both card entrances
/// go through this one parser and differ only in how they enter; any other shape is nil and only opens the app — including
/// `dearby://contacts/github?code=…`, which `ASWebAuthenticationSession` consumes inside GitHub 연결 (contract "연락처 인증" 2026-10-26).
enum IncomingLink: Equatable {
    /// `dearby://cards/…`: the user chose to save, so it is saved right away.
    case saveCard(WalletModel.Incoming)
    /// `<web origin>/cards/…`: the received detail when already saved, else 공유받은 명함 보기.
    case viewCard(WalletModel.Incoming)
    /// `/activities/<id>`: the numeric id (`GET /v1/activities/:id`).
    case activity(String)
    /// `dearby://favorites` (the `오늘 일정 n개` notification sends `?view=calendar`, contract "프로그램·활동별 알림 수준" 2026-10-25): 즐겨찾기,
    /// which is the calendar only since 2026-10-26, so `view` is not read.
    case favorites
    /// `dearby://timetable/<activityId>/<scheduleId>` (contract "행사 당일 진행 중 표시" 2026-10-26, `LiveSessionAttributes.link`): a tapped
    /// Live Activity opens that activity's detail with the block's timetable sheet on 내 시간표, as a tapped 세션 이동 알림 does.
    case timetable(activityID: String, scheduleID: String)

    init?(url: URL, web: URL) {
        guard let parts = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        let path = parts.path.split(separator: "/").map(String.init)
        let segments: [String]
        if parts.scheme == "dearby", let host = parts.host {
            segments = [host] + path
        } else if parts.scheme == web.scheme, parts.host?.lowercased() == web.host, parts.port == web.port {
            segments = path
        } else {
            return nil
        }
        if segments == ["favorites"] {
            self = .favorites
            return
        }
        if parts.scheme == "dearby", segments.count == 3, segments[0] == "timetable", Int64(segments[1]) != nil, Int64(segments[2]) != nil {
            self = .timetable(activityID: segments[1], scheduleID: segments[2])
            return
        }
        switch segments.count == 2 ? segments[0] : nil {
        case "cards":
            guard let card = WalletModel.Incoming(cardID: segments[1], query: parts.queryItems ?? []) else { return nil }
            self = parts.scheme == "dearby" ? .saveCard(card) : .viewCard(card)
        // Activities come only as universal links; `dearby://` carries cards, 즐겨찾기 and the Live Activity's timetable.
        case "activities" where parts.scheme != "dearby" && Int64(segments[1]) != nil:
            self = .activity(segments[1])
        default:
            return nil
        }
    }
}
