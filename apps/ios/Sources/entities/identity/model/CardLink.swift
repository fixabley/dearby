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
