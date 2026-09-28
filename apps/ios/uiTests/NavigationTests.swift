import XCTest

@MainActor final class NavigationTests: XCTestCase {
    func testGuestTabsAndLoginGate() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["tab-0"].waitForExistence(timeout: 10))
        capture("발견")
        app.buttons["tab-1"].tap()
        XCTAssertTrue(app.navigationBars["저장"].waitForExistence(timeout: 3))
        capture("저장")
        app.buttons["tab-2"].tap()
        XCTAssertTrue(app.buttons["새 명함 만들기"].waitForExistence(timeout: 3))
        capture("QR 새 명함")
        app.buttons["새 명함 만들기"].tap()
        XCTAssertTrue(app.navigationBars["이메일 로그인"].waitForExistence(timeout: 3))
        capture("로그인 필요")
        app.buttons["닫기"].tap()
        app.buttons["tab-3"].tap()
        XCTAssertTrue(app.staticTexts["받은 명함이 없어요"].waitForExistence(timeout: 3))
        capture("받은 명함 빈 상태")
        app.buttons["tab-4"].tap()
        XCTAssertTrue(app.buttons["이메일 로그인"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["편집"].exists)
        capture("내 프로필 로그인 안내")
    }
    func testAuthenticatedProfileAndQR() throws {
        let app = XCUIApplication()
        app.launch()
        app.buttons["tab-4"].tap()
        guard app.buttons["편집"].waitForExistence(timeout: 5) else {
            throw XCTSkip("Requires explicit real local API integration login")
        }
        XCTAssertTrue(app.staticTexts["iOS 검증 계정"].exists)
        capture("실제 API 로그인 프로필")
        app.buttons["편집"].tap()
        XCTAssertTrue(app.textFields["이름"].waitForExistence(timeout: 3))
        capture("프로필 연락처 이력 편집")
        app.buttons["취소"].tap()
        app.buttons["tab-2"].tap()
        XCTAssertTrue(app.buttons["네트워킹"].waitForExistence(timeout: 5))
        app.buttons["네트워킹"].tap()
        XCTAssertTrue(app.buttons["QR만 크게 보기"].waitForExistence(timeout: 3))
        capture("실제 발행 명함 QR")
        app.buttons["명함 공유"].tap()
        XCTAssertTrue(app.navigationBars["명함 공유"].waitForExistence(timeout: 3))
        capture("명함 공유 메뉴")
        app.buttons["닫기"].tap()
        app.buttons["QR만 크게 보기"].tap()
        capture("QR 단독 확대")
        app.buttons["QR 확대. 두 번 탭하여 닫기"].tap()
        app.buttons["명함 보기"].tap()
        XCTAssertTrue(app.buttons["공개 이메일: ios-visible@example.test"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["비공개 이메일: ios-private@example.test"].exists)
        capture("선택 공개 명함 상세")
        app.buttons["닫기"].tap()
        app.buttons["tab-3"].tap()
        XCTAssertTrue(app.buttons["나도 명함 주기"].waitForExistence(timeout: 3))
        capture("실제 서버 받은 명함")
        if app.buttons["다음 명함"].isEnabled {
            reveal(app.buttons["다음 명함"], in: app)
            app.buttons["다음 명함"].tap()
            XCTAssertTrue(app.staticTexts["card-deck-position"].label.hasPrefix("2 /"))
            capture("이력 포함 다음 명함")
            scrollToTop(app)
            capture("받은 명함 상단 구성")
            reveal(app.buttons["이전 명함"], in: app)
            app.buttons["이전 명함"].tap()
        }
        app.buttons["나도 명함 주기"].tap()
        XCTAssertTrue(app.navigationBars["내 명함 선택"].waitForExistence(timeout: 5))
        capture("내 명함 선택 상단 구성")
        let nextButtons = app.buttons.matching(identifier: "다음 명함")
        let next = nextButtons.element(boundBy: nextButtons.count - 1)
        if next.isEnabled { reveal(next, in: app); next.tap() }
        capture("실제 내 명함 선택")
        app.buttons["새 명함"].tap()
        XCTAssertTrue(app.textFields["명함 이름"].waitForExistence(timeout: 5))
        capture("명함 공개 범위 편집")
        let all = app.buttons["전체 선택"]
        reveal(all, in: app)
        all.tap()
        XCTAssertEqual(all.value as? String, "전체 선택됨")
        XCTAssertEqual(app.buttons["공개 이메일"].value as? String, "숨김", "History select-all must not expose contacts")
        capture("이력 전체 선택")
        all.tap()
        XCTAssertEqual(all.value as? String, "선택 안 됨")
    }
    func testLargeTextNavigationAndPrivacy() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launch()
        app.buttons["tab-4"].tap()
        guard app.buttons["편집"].waitForExistence(timeout: 5) else { throw XCTSkip("Requires explicit local API test account") }
        capture("큰 글자 프로필")
        app.buttons["tab-2"].tap()
        let card = app.buttons["iOS 공개 명함"]
        for _ in 0..<6 { if card.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(card.waitForExistence(timeout: 5))
        card.tap()
        for _ in 0..<6 { if app.buttons["QR만 크게 보기"].isHittable { break }; app.swipeDown() }
        capture("큰 글자 QR")
        app.buttons["QR만 크게 보기"].tap()
        XCTAssertTrue(app.buttons["QR 확대. 두 번 탭하여 닫기"].waitForExistence(timeout: 5))
        app.buttons["QR 확대. 두 번 탭하여 닫기"].tap()
        app.buttons["tab-3"].tap()
        XCTAssertTrue(app.buttons["나도 명함 주기"].waitForExistence(timeout: 5))
        capture("큰 글자 받은 명함")
        app.buttons["나도 명함 주기"].tap()
        XCTAssertTrue(app.navigationBars["내 명함 선택"].waitForExistence(timeout: 5))
        capture("큰 글자 내 명함 선택")
    }
    private func scrollToTop(_ app: XCUIApplication) {
        for _ in 0..<3 {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.3))
                .press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.8)))
        }
    }
    private func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<12 {
            if element.exists && element.isHittable && element.frame.minY > 130 && element.frame.maxY < app.frame.height - 200 { return }
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.72))
                .press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.3)))
        }
    }
    private func capture(_ name: String) {
        Thread.sleep(forTimeInterval: 0.4) // Let native sheet/stack animations settle for evidence.
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
