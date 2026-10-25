import Foundation

/// A link the app was opened with (contract "유니버설 링크·App Links·활동 공유·오픈그래프" 2026-10-22): `dearby://cards/…` from the web
/// card page's "Dearby 앱에 저장", or a universal link on the build's web origin for `/cards/*` and `/activities/*`. Both card entrances
/// go through this one parser and differ only in how they enter; any other shape is nil and only opens the app.
enum IncomingLink: Equatable {
    /// `dearby://cards/…`: the user chose to save, so it is saved right away.
    case saveCard(WalletModel.Incoming)
    /// `<web origin>/cards/…`: the received detail when already saved, else 공유받은 명함 보기.
    case viewCard(WalletModel.Incoming)
    /// `/activities/<id>`: the numeric id (`GET /v1/activities/:id`).
    case activity(String)
    /// `dearby://favorites?view=calendar` (the `오늘 일정 n개` notification, contract "프로그램·활동별 알림 수준" 2026-10-25): 즐겨찾기, as the
    /// calendar when `view=calendar`.
    case favorites(calendar: Bool)

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
            self = .favorites(calendar: parts.queryItems?.contains { $0.name == "view" && $0.value == "calendar" } ?? false)
            return
        }
        switch segments.count == 2 ? segments[0] : nil {
        case "cards":
            guard let card = WalletModel.Incoming(cardID: segments[1], query: parts.queryItems ?? []) else { return nil }
            self = parts.scheme == "dearby" ? .saveCard(card) : .viewCard(card)
        // Activities come only as universal links; `dearby://` carries cards alone.
        case "activities" where parts.scheme != "dearby" && Int64(segments[1]) != nil:
            self = .activity(segments[1])
        default:
            return nil
        }
    }
}
