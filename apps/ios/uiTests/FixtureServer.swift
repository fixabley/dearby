import Foundation
import Network

/// Serves `GET /v1/catalog` from `CatalogFixture` to the app under test (Debug `DEARBY_API_ORIGIN` override).
final class FixtureServer: @unchecked Sendable {
    private let listener: NWListener
    private let queue = DispatchQueue(label: "fixture-server")
    /// When true every request answers 503, to exercise the error and retry state.
    var failing = false
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

    private func serve(_ connection: NWConnection) {
        connection.start(queue: queue)
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] data, _, _, _ in
            guard let self else { return }
            let request = String(decoding: data ?? Data(), as: UTF8.self)
            let ok = !failing && request.hasPrefix("GET /v1/catalog ")
            let body = ok ? CatalogFixture.json : Data("{}".utf8)
            let head = "HTTP/1.1 \(ok ? "200 OK" : "503 Service Unavailable")\r\nContent-Type: application/json\r\n" +
                "Content-Length: \(body.count)\r\nConnection: close\r\n\r\n"
            connection.send(content: Data(head.utf8) + body, completion: .contentProcessed { _ in connection.cancel() })
        }
    }
}
