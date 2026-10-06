import Foundation
import Network

/// Serves the catalog from `CatalogFixture` and a minimal account API to the app under test
/// (Debug `DEARBY_API_ORIGIN` override). Code `123456` signs in; anything else is rejected.
final class FixtureServer: @unchecked Sendable {
    static let code = "123456"
    private let listener: NWListener
    private let queue = DispatchQueue(label: "fixture-server")
    /// When true every request answers 503, to exercise the error and retry state.
    var failing = false
    /// When true `POST /v1/cards` answers 503 while the profile still saves.
    var failingCards = false
    /// "METHOD /path" of every request, in order.
    private(set) var requests: [String] = []
    private var profile = #"{"id":"p1","name":"","job":"","introduction":"","contacts":[],"histories":[],"updatedAt":"2026-10-06T00:00:00Z"}"#
    static let shareID = "5a1e0000-0000-4000-8000-000000000001"
    /// Cards published through this server, in creation order.
    private var cards: [String] = []
    private(set) var port: UInt16 = 0
    var origin: String { "http://127.0.0.1:\(port)" }

    init() throws {
        listener = try NWListener(using: .tcp, on: .any)
        let ready = DispatchSemaphore(value: 0)
        listener.stateUpdateHandler = { if case .ready = $0 { ready.signal() } }
        listener.newConnectionHandler = { [weak self] in self?.serve($0) }
        listener.start(queue: queue)
        _ = ready.wait(timeout: .now() + 5)
        port = listener.port?.rawValue ?? 0
    }
    deinit { listener.cancel() }
    func recorded() -> [String] { queue.sync { requests } }

    private func serve(_ connection: NWConnection, received: Data = Data()) {
        if received.isEmpty { connection.start(queue: queue) }
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] data, _, done, _ in
            guard let self else { return }
            let buffer = received + (data ?? Data())
            // Wait for the whole body: headers and body can arrive in separate reads.
            guard let split = buffer.range(of: Data("\r\n\r\n".utf8)) else {
                if !done { serve(connection, received: buffer) }
                return
            }
            let head = String(decoding: buffer[..<split.lowerBound], as: UTF8.self)
            let length = head.split(separator: "\r\n").first { $0.lowercased().hasPrefix("content-length:") }
                .flatMap { Int($0.split(separator: ":")[1].trimmingCharacters(in: .whitespaces)) } ?? 0
            let body = buffer[split.upperBound...]
            if body.count < length && !done { serve(connection, received: buffer); return }
            let (status, reply) = respond(head.split(separator: " ").prefix(2).joined(separator: " "), Data(body))
            let text = "HTTP/1.1 \(status) X\r\nContent-Type: application/json\r\nContent-Length: \(reply.utf8.count)\r\nConnection: close\r\n\r\n"
            connection.send(content: Data((text + reply).utf8), completion: .contentProcessed { _ in connection.cancel() })
        }
    }
    private func respond(_ route: String, _ body: Data) -> (Int, String) {
        requests.append(route)
        if failing { return (503, "{}") }
        let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any] ?? [:]
        switch route {
        case "GET /v1/catalog": return (200, String(decoding: CatalogFixture.json, as: UTF8.self))
        case "POST /v1/auth/challenges": return (202, #"{"challengeId":"e1000000-0000-4000-8000-000000000001","expiresAt":"2026-10-06T00:05:00Z"}"#)
        case "POST /v1/auth/sessions":
            return json["code"] as? String == Self.code ? (200, #"{"sessionToken":"fixture-token","profileId":"p1"}"#) : (401, "{}")
        case "GET /v1/profile": return (200, profile)
        case "PUT /v1/profile":
            var saved = json
            saved["id"] = "p1"; saved["updatedAt"] = "2026-10-06T00:00:00Z"
            profile = String(decoding: (try? JSONSerialization.data(withJSONObject: saved)) ?? Data(), as: UTF8.self)
            return (200, profile)
        case "POST /v1/cards":
            if failingCards { return (503, "{}") }
            let name = (try? JSONSerialization.jsonObject(with: Data(profile.utf8)) as? [String: Any])?["name"] as? String ?? ""
            let card = #"{"id":"c1000000-0000-4000-8000-00000000000\#(cards.count + 1)","name":"내 명함","description":"","profileName":"\#(name)","job":"","contacts":[],"histories":[],"createdAt":"2026-10-06T00:00:00Z"}"#
            cards.append(card)
            return (201, card)
        case "GET /v1/cards": return (200, #"{"items":[\#(cards.joined(separator: ","))]}"#)
        default:
            // POST /v1/cards/<id>/shares: activities come back as an {id, title} snapshot.
            if route.hasPrefix("POST /v1/cards/"), route.hasSuffix("/shares") {
                let cardID = route.split(separator: "/")[3]
                let ids = json["activityIds"] as? [String] ?? []
                let activities = ids.map { #"{"id":"\#($0)","title":"활동 \#($0)"}"# }.joined(separator: ",")
                return (201, #"{"id":"\#(Self.shareID)","cardId":"\#(cardID)","activities":[\#(activities)],"createdAt":"2026-10-06T00:00:00Z"}"#)
            }
            return (404, "{}")
        }
    }
}
