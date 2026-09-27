import Foundation

struct CardLink: Equatable {
    let cardID: String
    let context: ExchangeContextModel

    var url: URL? {
        var components = URLComponents()
        components.scheme = "dearby"
        components.host = "card"
        components.path = "/" + cardID
        if let activityID = context.activityId { components.queryItems = [URLQueryItem(name: "activityId", value: activityID)] } else if let label = context.label { components.queryItems = [URLQueryItem(name: "label", value: label)] }
        // Components above are controlled, but keep malformed local input non-crashing.
        return components.url
    }
    func url(relativeTo base: URL) -> URL? {
        guard base.scheme == "https", base.host != nil, base.user == nil, base.password == nil,
              base.query == nil, base.fragment == nil,
              var target = URLComponents(url: base.appendingPathComponent(cardID), resolvingAgainstBaseURL: false),
              let original = url.flatMap({ URLComponents(url: $0, resolvingAgainstBaseURL: false) }) else { return nil }
        target.queryItems = original.queryItems
        return target.url
    }
    init(parsing url: URL, shareBase: URL) throws {
        guard url.scheme == "https", url.scheme == shareBase.scheme, url.host == shareBase.host,
              url.port == shareBase.port, url.user == nil, url.password == nil, url.fragment == nil,
              url.deletingLastPathComponent().path == shareBase.path,
              var normalized = URLComponents(url: url, resolvingAgainstBaseURL: false) else { throw ParseError.invalid }
        normalized.scheme = "dearby"
        normalized.host = "card"
        normalized.port = nil
        normalized.path = "/" + url.lastPathComponent
        guard let customURL = normalized.url else { throw ParseError.invalid }
        try self.init(parsing: customURL)
    }
    init(cardID: String, context: ExchangeContextModel) {
        self.cardID = cardID; self.context = context
    }
    init(parsing url: URL) throws {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              components.scheme == "dearby", components.host == "card",
              components.user == nil, components.password == nil, components.port == nil,
              components.fragment == nil else { throw ParseError.invalid }
        let parts = components.path.split(separator: "/", omittingEmptySubsequences: false)
        guard parts.count == 2, parts[0].isEmpty, UUID(uuidString: String(parts[1])) != nil else { throw ParseError.invalid }
        let items = components.queryItems ?? []
        guard items.count <= 1, items.allSatisfy({ ["label", "activityId"].contains($0.name) && $0.value != nil }) else {
            throw ParseError.invalid
        }
        let label = items.first(where: { $0.name == "label" })?.value
        let activityID = items.first(where: { $0.name == "activityId" })?.value
        if let label, label.isEmpty || label.count > 200 { throw ParseError.invalid }
        if let activityID, UUID(uuidString: activityID) == nil { throw ParseError.invalid }
        cardID = String(parts[1])
        context = ExchangeContextModel(activityId: activityID, label: label)
    }
    enum ParseError: LocalizedError {
        case invalid
        var errorDescription: String? { "올바른 Dearby 명함 링크가 아닙니다." }
    }
}
