import XCTest

@MainActor final class DiscoveryNavigationTests: XCTestCase {
    func testDiscoveryFiltersAndFiveTabs() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["활동 둘러보기"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["activity-conference"].exists)
        XCTAssertTrue(app.buttons["activity-camp"].exists)
        XCTAssertTrue(app.buttons["activity-meetup"].exists)
        for index in 0...4 { XCTAssertTrue(app.buttons["tab-\(index)"].exists) }
        for title in ["이메일 로그인", "프로그램 저장", "새 명함 만들기"] { XCTAssertFalse(app.buttons[title].exists) }
        capture(app, "prototype-discovery")
        app.buttons["선발형"].tap()
        XCTAssertTrue(app.buttons["activity-conference"].waitForNonExistence(timeout: 5))
        XCTAssertTrue(app.buttons["activity-camp"].exists)
        app.buttons["참가등록형"].tap()
        XCTAssertTrue(app.buttons["activity-camp"].waitForNonExistence(timeout: 5))
        XCTAssertTrue(app.buttons["activity-conference"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["activity-meetup"].exists)
    }
    func testApplicationCompletionIsLocalAndResetsOnRelaunch() {
        let app = XCUIApplication()
        app.launch()
        // The conference starts applied, so this flow uses the camp.
        XCTAssertTrue(app.buttons["activity-camp"].waitForExistence(timeout: 10))
        app.buttons["activity-camp"].tap()
        Thread.sleep(forTimeInterval: 0.6)
        capture(app, "prototype-detail")
        app.buttons["open-application"].tap()
        XCTAssertTrue(app.navigationBars["신청 (예시)"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["예시 링크를 외부 브라우저로 열기"].waitForExistence(timeout: 5))
        capture(app, "prototype-application")
        app.buttons["닫기"].tap()
        XCTAssertTrue(app.navigationBars["신청 (예시)"].waitForNonExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["데모 신청 완료 · 실제 접수가 아닙니다"].exists)
        app.buttons["open-application"].tap()
        app.buttons["데모 신청 완료로 표시"].tap()
        XCTAssertTrue(app.staticTexts["데모 신청 완료 · 실제 접수가 아닙니다"].waitForExistence(timeout: 5))
        capture(app, "activity-applied")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["activity-camp"].tap()
        XCTAssertTrue(app.staticTexts["데모 신청 완료 · 실제 접수가 아닙니다"].waitForExistence(timeout: 5))
        app.terminate()
        app.launch()
        app.buttons["activity-camp"].tap()
        XCTAssertTrue(app.navigationBars["활동 상세"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["데모 신청 완료 · 실제 접수가 아닙니다"].exists)
    }
    func testCalendarExampleWithAndWithoutOverlap() {
        let app = XCUIApplication()
        for id in ["conference", "camp"] {
            app.launch()
            XCTAssertTrue(app.buttons["activity-\(id)"].waitForExistence(timeout: 10))
            app.buttons["activity-\(id)"].tap()
            let calendar = app.buttons["겹치는 시간 확인하기"]
            for _ in 0..<8 where !calendar.isHittable { app.swipeUp() }
            XCTAssertTrue(calendar.isHittable)
            capture(app, "activity-schedule")
            calendar.tap()
            XCTAssertTrue(app.buttons["선택한 캘린더로 확인"].waitForExistence(timeout: 5))
            XCTAssertFalse(XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch.exists)
            // Tap the switch itself: the element's center can fall on the row label, which does not toggle.
            let toggle = app.switches["예시 캘린더"].switches.firstMatch
            toggle.tap()
            let disabled = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == false"),
                                                     object: app.buttons["선택한 캘린더로 확인"])
            XCTAssertEqual(XCTWaiter.wait(for: [disabled], timeout: 5), .completed)
            toggle.tap()
            app.buttons["선택한 캘린더로 확인"].tap()
            let result = id == "conference" ? "60분이 겹쳐요" : "예시 캘린더와 겹치는 시간이 없어요"
            XCTAssertTrue(app.staticTexts[result].waitForExistence(timeout: 5))
            XCTAssertTrue(app.buttons["선택한 캘린더로 확인"].waitForNonExistence(timeout: 5))
            XCTAssertTrue(app.switches["예시 캘린더"].waitForNonExistence(timeout: 5))
            if id == "conference" { XCTAssertTrue(app.staticTexts["1 / 1"].exists) }
            // Sheet detent animation can still be drawing after accessibility settles.
            Thread.sleep(forTimeInterval: 0.6)
            capture(app, "timeline-calendar-\(id)")
            app.buttons["확인했어요"].tap()
            XCTAssertTrue(app.navigationBars["활동 상세"].waitForExistence(timeout: 5))
            app.terminate()
        }
    }
    func testAccessibilityTextCalendarAndWalletRemainUsable() {
        let app = XCUIApplication()
        app.launchArguments = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launch(); app.buttons["activity-conference"].tap()
        let calendar = app.buttons["겹치는 시간 확인하기"]
        for _ in 0..<15 where !calendar.isHittable { app.swipeUp() }
        // A partially exposed large label may be hittable while its center is behind the sticky CTA.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.65))
            .press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.45)))
        Thread.sleep(forTimeInterval: 0.4)
        XCTAssertTrue(calendar.isHittable); calendar.tap()
        XCTAssertTrue(app.buttons["선택한 캘린더로 확인"].waitForExistence(timeout: 5))
        app.buttons["선택한 캘린더로 확인"].tap()
        XCTAssertTrue(app.staticTexts["60분이 겹쳐요"].waitForExistence(timeout: 5))
        Thread.sleep(forTimeInterval: 0.6)
        capture(app, "calendar-accessibility-text")
        XCTAssertTrue(app.buttons["확인했어요"].isHittable); app.buttons["확인했어요"].tap()
        app.navigationBars.buttons.element(boundBy: 0).tap(); app.buttons["tab-3"].tap()
        let detail = app.buttons["명함 상세보기"]
        for _ in 0..<15 where !detail.isHittable { app.swipeUp() }
        XCTAssertTrue(detail.isHittable)
        capture(app, "wallet-accessibility-text")
        detail.tap(); XCTAssertTrue(app.navigationBars["공유 카드"].waitForExistence(timeout: 5))
    }
    private func capture(_ app: XCUIApplication, _ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name; shot.lifetime = .keepAlways; add(shot)
    }
}
