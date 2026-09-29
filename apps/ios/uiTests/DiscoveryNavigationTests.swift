import XCTest

@MainActor final class DiscoveryNavigationTests: XCTestCase {
    func testOnlyDiscoveryIsVisible() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["모집 중인 활동"].waitForExistence(timeout: 10))
        for index in 0...4 { XCTAssertFalse(app.buttons["tab-\(index)"].exists) }
        XCTAssertFalse(app.buttons["이메일 로그인"].exists)
        XCTAssertFalse(app.buttons["프로그램 저장"].exists)
        XCTAssertFalse(app.buttons["새 명함 만들기"].exists)
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "탐색만 노출"; shot.lifetime = .keepAlways; add(shot)
    }
    func testDetailCalendarAndApplicationFlow() throws {
        let app = XCUIApplication()
        app.launch()
        let activity = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'activity-'")).firstMatch
        guard activity.waitForExistence(timeout: 8) else { throw XCTSkip("Requires explicit current test catalog fixture in dedicated Simulator") }
        activity.tap()
        let calendar = app.buttons["겹치는 시간 확인하기"]
        for _ in 0..<8 where !calendar.isHittable { app.swipeUp() }
        XCTAssertTrue(calendar.isHittable)
        XCTAssertFalse(app.buttons["프로그램 저장"].exists)
        XCTAssertFalse(app.buttons["조직 저장"].exists)
        calendar.tap()
        XCTAssertTrue(app.navigationBars["겹치는 시간 확인하기"].waitForExistence(timeout: 5))
        let permission = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        if permission.waitForExistence(timeout: 2) {
            let allow = permission.buttons.matching(NSPredicate(format: "label CONTAINS '전체' OR label CONTAINS 'Full'")).firstMatch
            XCTAssertTrue(allow.exists)
            allow.tap()
        }
        let compare = app.buttons["선택한 캘린더로 확인"]
        if compare.waitForExistence(timeout: 3) { compare.tap() }
        capture(app, "calendar")
        app.buttons["닫기"].tap()
        XCTAssertTrue(app.navigationBars["활동 상세"].waitForExistence(timeout: 5))
        capture(app, "detail")
        let apply = app.buttons["신청 페이지 열기"]
        XCTAssertTrue(apply.isHittable)
        apply.tap()
        let done = app.buttons["Done"]
        let korean = app.buttons["완료"]
        if app.buttons["닫기"].waitForExistence(timeout: 3) { app.buttons["닫기"].tap() } else if done.waitForExistence(timeout: 5) { done.tap() } else {
            XCTAssertTrue(korean.waitForExistence(timeout: 5)); korean.tap()
        }
        XCTAssertTrue(app.buttons["나중에"].waitForExistence(timeout: 5))
        app.buttons["나중에"].tap()
        XCTAssertFalse(app.staticTexts["이 활동은 이미 신청한 활동이에요."].exists)
    }
    private func capture(_ app: XCUIApplication, _ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name; shot.lifetime = .keepAlways; add(shot)
    }
}
