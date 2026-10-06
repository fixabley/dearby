import XCTest
@testable import Dearby

final class AppLinksTests: XCTestCase {
    private let web = URL(string: "https://web.example.test")!
    private let id = "d0000000-0000-4000-8000-000000000001"

    func testReleaseOriginsMustBeHTTPSDomains() {
        XCTAssertNotNil(AppOrigins.parse("https://api.example.test", debug: false))
        for raw in ["", "http://api.example.test", "https://10.0.0.1", "https://api.example.test:8443",
                    "https://api.example.test/v1", "https://API.example.test", "https://localhost", "http://localhost:3000"] {
            XCTAssertNil(AppOrigins.parse(raw, debug: false), raw)
        }
    }
    func testDebugAllowsLocalServersAndFallsBackWhenUnset() {
        XCTAssertNotNil(AppOrigins.parse("http://localhost:3000", debug: true))
        XCTAssertNotNil(AppOrigins.parse("http://127.0.0.1:3210", debug: true))
        XCTAssertNil(AppOrigins.parse("http://192.168.0.2:3000", debug: true))
        XCTAssertEqual(AppOrigins.load(["DearbyAPIOrigin": "", "DearbyWebOrigin": ""], debug: true)?.web.absoluteString,
                       "http://localhost:3210")
        XCTAssertNil(AppOrigins.load(["DearbyAPIOrigin": "", "DearbyWebOrigin": ""], debug: false))
    }
    func testShareLinksOnlyMatchTheWebOriginAndAUUID() {
        func shareID(_ raw: String) -> String? { SharedCardLink.shareID(from: URL(string: raw)!, web: web) }
        XCTAssertEqual(shareID("https://web.example.test/s/\(id)"), id)
        XCTAssertEqual(shareID("https://web.example.test/s/\(id.uppercased())/?utm=x#top"), id)
        for raw in ["https://other.example.test/s/\(id)", "http://web.example.test/s/\(id)",
                    "https://web.example.test/cards/\(id)", "https://web.example.test/s/not-a-uuid",
                    "https://web.example.test/s/\(id)/extra", "https://web.example.test:8443/s/\(id)"] {
            XCTAssertNil(shareID(raw), raw)
        }
    }
}
