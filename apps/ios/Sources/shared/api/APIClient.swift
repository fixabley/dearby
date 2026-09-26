import Foundation

struct APIClient: Sendable {
    var baseURL: URL?
    var session: URLSession = .shared
    static var configured: APIClient {
        let raw = Bundle.main.object(forInfoDictionaryKey: "DearbyAPIURL") as? String ?? ""
        return APIClient(baseURL: validatedURL(raw))
    }
    static func validatedURL(_ raw: String) -> URL? {
        guard let url = URL(string: raw), url.host != nil, url.user == nil, url.password == nil else { return nil }
        if url.scheme == "https" { return url }
        #if DEBUG
        if url.scheme == "http", ["localhost", "127.0.0.1", "::1"].contains(url.host ?? "") { return url }
        #endif
        return nil
    }
    func request<Response: Decodable & Sendable>(_ method: String, _ path: String,
        token: String? = nil, body: Data? = nil, as type: Response.Type = Response.self) async throws -> Response {
        guard let baseURL else { throw APIError.unconfigured }
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = method
        request.timeoutInterval = 25
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else { throw APIError.status(http.statusCode) }
        if Response.self == EmptyResponse.self { return EmptyResponse() as! Response }
        return try JSONDecoder().decode(Response.self, from: data)
    }
}
struct EmptyResponse: Decodable, Sendable {}
struct Items<Value: Codable & Sendable>: Codable, Sendable { var items: [Value] }
enum APIError: LocalizedError {
    case unconfigured, invalidResponse, status(Int), loginRequired
    var errorDescription: String? {
        switch self {
        case .unconfigured: "서버가 설정되지 않았습니다. 기기 저장만 사용할 수 있습니다."
        case .invalidResponse: "서버 응답을 확인할 수 없습니다. 완료로 처리하지 않았습니다."
        case .loginRequired: "이메일 로그인이 필요합니다."
        case .status(let code): "서버 요청 실패 (\(code)). 저장된 정보는 유지됩니다."
        }
    }
}
