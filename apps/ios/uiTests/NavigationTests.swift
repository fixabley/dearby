import XCTest

@MainActor final class NavigationTests: XCTestCase {
    func testGuestTabsAndLoginGate() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.tabBars.buttons["발견"].waitForExistence(timeout: 10))
        capture("발견")
        app.tabBars.buttons["저장"].tap()
        XCTAssertTrue(app.navigationBars["저장"].waitForExistence(timeout: 3))
        capture("저장")
        app.tabBars.buttons["QR"].tap()
        XCTAssertTrue(app.buttons["새 명함 만들기"].waitForExistence(timeout: 3))
        capture("QR 새 명함")
        app.buttons["새 명함 만들기"].tap()
        XCTAssertTrue(app.navigationBars["이메일 로그인"].waitForExistence(timeout: 3))
        capture("로그인 필요")
        app.buttons["닫기"].tap()
        app.tabBars.buttons["받은 명함"].tap()
        XCTAssertTrue(app.staticTexts["받은 명함이 없어요"].waitForExistence(timeout: 3))
        capture("받은 명함 빈 상태")
        app.tabBars.buttons["내 프로필"].tap()
        XCTAssertTrue(app.buttons["이메일 로그인"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["편집"].exists)
        capture("내 프로필 로그인 안내")
    }
    func testAuthenticatedProfileAndQR() throws {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["내 프로필"].tap()
        guard app.buttons["편집"].waitForExistence(timeout: 5) else {
            throw XCTSkip("Requires explicit real local API integration login")
        }
        XCTAssertTrue(app.staticTexts["iOS 검증 계정"].exists)
        capture("실제 API 로그인 프로필")
        app.buttons["편집"].tap()
        XCTAssertTrue(app.textFields["이름"].waitForExistence(timeout: 3))
        capture("프로필 연락처 이력 편집")
        app.buttons["취소"].tap()
        app.tabBars.buttons["QR"].tap()
        XCTAssertTrue(app.buttons["iOS 공개 명함"].waitForExistence(timeout: 5))
        app.buttons["iOS 공개 명함"].tap()
        XCTAssertTrue(app.buttons["QR만 크게 보기"].waitForExistence(timeout: 3))
        capture("실제 발행 명함 QR")
        app.buttons["QR만 크게 보기"].tap()
        capture("QR 단독 확대")
        app.buttons["QR 확대. 두 번 탭하여 닫기"].tap()
        app.buttons["명함 보기"].tap()
        app.buttons["상세보기"].tap()
        XCTAssertTrue(app.buttons["공개 이메일: ios-visible@example.test"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["비공개 이메일: ios-private@example.test"].exists)
        capture("선택 공개 명함 상세")
        app.buttons["닫기"].tap()
        app.tabBars.buttons["받은 명함"].tap()
        XCTAssertTrue(app.buttons["나도 명함 주기"].waitForExistence(timeout: 3))
        capture("실제 서버 받은 명함")
    }
    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
