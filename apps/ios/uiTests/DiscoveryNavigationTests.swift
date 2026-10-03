import XCTest

@MainActor final class DiscoveryNavigationTests: XCTestCase {
    func testDiscoveryFiltersAndHiddenServiceEntrypoints() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["활동 둘러보기"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["activity-conference"].exists)
        XCTAssertTrue(app.buttons["activity-camp"].exists)
        XCTAssertTrue(app.buttons["activity-meetup"].exists)
        for index in 0...4 { XCTAssertFalse(app.buttons["tab-\(index)"].exists) }
        for title in ["이메일 로그인", "프로그램 저장", "새 명함 만들기"] { XCTAssertFalse(app.buttons[title].exists) }
        capture(app, "prototype-discovery")
        app.buttons["선발형"].tap()
        XCTAssertTrue(app.buttons["activity-camp"].exists)
        XCTAssertFalse(app.buttons["activity-conference"].exists)
        app.buttons["참가등록형"].tap()
        XCTAssertTrue(app.buttons["activity-conference"].exists)
        XCTAssertTrue(app.buttons["activity-meetup"].exists)
        XCTAssertFalse(app.buttons["activity-camp"].exists)
    }
    func testApplicationCompletionIsLocalAndResetsOnRelaunch() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["activity-conference"].waitForExistence(timeout: 10))
        app.buttons["activity-conference"].tap()
        capture(app, "prototype-detail")
        app.buttons["open-application"].tap()
        XCTAssertTrue(app.navigationBars["신청 (예시)"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["예시 링크를 외부 브라우저로 열기"].exists)
        capture(app, "prototype-application")
        app.buttons["닫기"].tap()
        XCTAssertFalse(app.staticTexts["데모 신청 완료 · 실제 접수가 아닙니다"].exists)
        app.buttons["open-application"].tap()
        app.buttons["데모 신청 완료로 표시"].tap()
        XCTAssertTrue(app.staticTexts["데모 신청 완료 · 실제 접수가 아닙니다"].waitForExistence(timeout: 5))
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["activity-conference"].tap()
        XCTAssertTrue(app.staticTexts["데모 신청 완료 · 실제 접수가 아닙니다"].exists)
        app.terminate()
        app.launch()
        app.buttons["activity-conference"].tap()
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
            calendar.tap()
            XCTAssertTrue(app.buttons["선택한 캘린더로 확인"].waitForExistence(timeout: 5))
            XCTAssertFalse(XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch.exists)
            let toggle = app.switches["예시 캘린더"]
            toggle.tap()
            XCTAssertFalse(app.buttons["선택한 캘린더로 확인"].isEnabled)
            toggle.tap()
            app.buttons["선택한 캘린더로 확인"].tap()
            let result = id == "conference" ? "겹치는 시간 1 / 1" : "예시 캘린더와 겹치는 시간이 없어요"
            XCTAssertTrue(app.staticTexts[result].waitForExistence(timeout: 5))
            capture(app, "prototype-calendar-\(id)")
            app.buttons["닫기"].tap()
            XCTAssertTrue(app.navigationBars["활동 상세"].exists)
            app.terminate()
        }
    }
    private func capture(_ app: XCUIApplication, _ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name; shot.lifetime = .keepAlways; add(shot)
    }
}
