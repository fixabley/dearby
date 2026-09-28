import XCTest
@testable import Dearby

final class ExchangeActivityTests: XCTestCase {
    func testRegisteredDirectAndNoneRemainExclusive() throws {
        var state = ExchangeActivityState()
        XCTAssertEqual(state.context, ExchangeContextModel())
        state.selection = "direct"
        state.label = "  팀 모임  "
        XCTAssertEqual(state.context, ExchangeContextModel(label: "팀 모임"))
        state.selection = CatalogFixture.activityID
        XCTAssertEqual(state.context, ExchangeContextModel(activityId: CatalogFixture.activityID))
        let encoded = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(state.context)) as? [String: Any])
        XCTAssertTrue(encoded["label"] is NSNull)
        state.selection = "none"
        XCTAssertEqual(state.context, ExchangeContextModel())
        state.selection = "direct"
        state.label = String(repeating: "가", count: 201)
        XCTAssertFalse(state.isValid)
    }
    func testHistoricalActivityLookupAndUnknownReference() throws {
        let historical = try CatalogFixture.catalog(changes: ["recruitmentStatus": "closed", "isRecruiting": false]).activities
        let context = ExchangeContextModel(activityId: CatalogFixture.activityID)
        XCTAssertEqual(ExchangeActivityState.title(context, activities: historical), "계약 테스트 활동")
        XCTAssertEqual(ExchangeActivityState.title(context, activities: []), "활동 정보 미확인")
        XCTAssertEqual(ExchangeActivityState.title(.init(label: "직접 입력"), activities: historical), "직접 입력")
        XCTAssertEqual(ExchangeActivityState.title(.init(), activities: historical), "활동 선택 안 함")
    }
    func testRegisteredContextSurvivesCustomAndConfiguredHTTPSLinks() throws {
        let base = try XCTUnwrap(URL(string: "https://example.test/card"))
        let id = UUID().uuidString
        for context in [ExchangeContextModel(activityId: CatalogFixture.activityID), .init(label: "모임 & 세션"), .init()] {
            let link = CardLink(cardID: id, context: context)
            XCTAssertEqual(try CardLink(parsing: XCTUnwrap(link.url)), link)
            let web = try XCTUnwrap(link.url(relativeTo: base))
            XCTAssertEqual(try CardLink(parsing: web, shareBase: base), link)
        }
        for raw in ["https://example.test/card/\(id)?activityId=invalid", "https://example.test/card/\(id)?label=a&activityId=\(id)",
                    "https://example.test/card/extra/\(id)", "https://evil.test/card/\(id)",
                    "https://user@example.test/card/\(id)", "https://example.test/card/\(id)#fragment"] {
            XCTAssertThrowsError(try CardLink(parsing: XCTUnwrap(URL(string: raw)), shareBase: base))
        }
    }
}
