import XCTest

@MainActor final class SharedCardTests: XCTestCase {
    override func setUpWithError() throws {
        throw XCTSkip("2026-09-29 기본 노출에서 제외된 명함/저장 흐름의 보존용 회귀입니다. 현재 흐름은 DiscoveryNavigationTests에서 검증합니다.")
    }

    func testGuestSaveConfirmationAndReturnLoginCancellation() throws {
        guard let link = ProcessInfo.processInfo.environment["DEARBY_AUDIT_CARD_URL"] else {
            throw XCTSkip("Requires explicit public card URL from local integration")
        }
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        app.buttons["tab-4"].tap()
        if app.buttons["편집"].waitForExistence(timeout: 3) {
            reveal(app.buttons["로그아웃"], in: app)
            app.buttons["로그아웃"].tap()
        }
        XCTAssertTrue(app.buttons["이메일 로그인"].waitForExistence(timeout: 5))
        app.buttons["tab-2"].tap()
        app.buttons["QR 찍기"].tap()
        let input = app.textFields["명함 링크 붙여넣기"]
        reveal(input, in: app)
        input.tap()
        input.typeText(link + "\n")
        reveal(app.buttons["명함 확인하고 저장"], in: app)
        app.buttons["명함 확인하고 저장"].tap()
        XCTAssertTrue(app.navigationBars["공유 카드"].waitForExistence(timeout: 10))
        capture("비로그인 실제 공유 명함")
        reveal(app.buttons["카드 저장"], in: app)
        app.buttons["카드 저장"].tap()
        XCTAssertTrue(app.buttons["기기에 저장"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "앱을 삭제하면 복구할 수 없어요")).firstMatch.exists)
        capture("기기 저장 복구불가 확인")
        app.buttons["취소"].tap()
        XCTAssertTrue(app.navigationBars["공유 카드"].exists)
        reveal(app.buttons["나도 카드 주기"], in: app)
        app.buttons["나도 카드 주기"].tap()
        XCTAssertTrue(app.navigationBars["이메일 로그인"].waitForExistence(timeout: 5))
        app.buttons["닫기"].tap()
        XCTAssertTrue(app.navigationBars["이메일 로그인"].waitForNonExistence(timeout: 5))
        XCTAssertFalse(app.buttons["이 명함 보내기"].exists)
        app.buttons["tab-4"].tap()
        XCTAssertTrue(app.buttons["이메일 로그인"].waitForExistence(timeout: 5))
        capture("로그인 취소 후 비로그인 유지")
        app.buttons["tab-3"].tap()
        XCTAssertTrue(app.staticTexts["받은 명함이 없어요"].waitForExistence(timeout: 5), "Cancelling must not save a guest ID")
    }
    private func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<14 {
            if element.exists && element.isHittable && element.frame.minY > 130 && element.frame.maxY < app.frame.height - 105 { return }
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.72))
                .press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.3)))
        }
    }
    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
