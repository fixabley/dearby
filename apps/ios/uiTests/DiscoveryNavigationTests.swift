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
        capture(app, "detail-top")
        let calendar = app.buttons["겹치는 시간 확인하기"]
        for _ in 0..<8 where !calendar.isHittable { app.swipeUp() }
        XCTAssertTrue(calendar.isHittable)
        XCTAssertFalse(app.buttons["프로그램 저장"].exists)
        XCTAssertFalse(app.buttons["조직 저장"].exists)
        capture(app, "detail-schedule")
        calendar.tap()
        XCTAssertTrue(app.navigationBars["겹치는 시간"].waitForExistence(timeout: 5))
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
    func testReferenceArtworkDetail() throws {
        let app = XCUIApplication()
        app.launch()
        let activity = app.buttons.matching(NSPredicate(format: "label CONTAINS 'FEConf 2026'")).firstMatch
        guard activity.waitForExistence(timeout: 8) else { throw XCTSkip("Requires explicit conference preview fixture") }
        activity.tap()
        XCTAssertTrue(app.navigationBars["활동 상세"].waitForExistence(timeout: 5))
        capture(app, "artwork-detail")
    }
    func testLargeTypeDiscoveryAndCalendarEntry() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launch()
        let activity = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'activity-'")).firstMatch
        guard activity.waitForExistence(timeout: 8) else { throw XCTSkip("Requires explicit conference preview fixture") }
        capture(app, "large-discovery")
        activity.tap()
        let calendar = app.buttons["겹치는 시간 확인하기"]
        for _ in 0..<24 {
            if calendar.isHittable && calendar.frame.maxY < app.buttons["신청 페이지 열기"].frame.minY { break }
            app.scrollViews.firstMatch.swipeUp()
        }
        XCTAssertTrue(calendar.isHittable)
        capture(app, "large-schedule")
        calendar.tap()
        XCTAssertTrue(app.navigationBars["겹치는 시간"].waitForExistence(timeout: 5))
        let compare = app.buttons["선택한 캘린더로 확인"]
        XCTAssertTrue(compare.waitForExistence(timeout: 5))
        for _ in 0..<12 where !compare.isHittable { app.swipeUp() }
        compare.tap()
        XCTAssertTrue(app.staticTexts["30분이 겹쳐요"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["확인했어요"].isHittable)
        capture(app, "calendar-a-large")
    }
    func testCalendarReferenceAPagingAndPrivacy() throws {
        let app = XCUIApplication()
        app.launch()
        let activity = app.buttons.matching(NSPredicate(format: "label CONTAINS '캘린더 겹침 체험'")).firstMatch
        guard activity.waitForExistence(timeout: 8) else { throw XCTSkip("Requires explicit calendar preview fixture") }
        activity.tap()
        let calendar = app.buttons["겹치는 시간 확인하기"]
        for _ in 0..<8 where !calendar.isHittable { app.swipeUp() }
        calendar.tap()
        let compare = app.buttons["선택한 캘린더로 확인"]
        XCTAssertTrue(compare.waitForExistence(timeout: 5))
        compare.tap()
        XCTAssertTrue(app.staticTexts["30분이 겹쳐요"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["overlap-position"].label, "1 / 2")
        XCTAssertFalse(app.staticTexts["[Dearby 데모] 팀 미팅"].exists)
        capture(app, "calendar-a-first")
        app.buttons["확인했어요"].tap()
        XCTAssertEqual(app.staticTexts["overlap-position"].label, "2 / 2")
        capture(app, "calendar-a-second")
        app.buttons["이전 겹치는 시간"].tap()
        XCTAssertEqual(app.staticTexts["overlap-position"].label, "1 / 2")
        app.buttons["확인했어요"].tap()
        app.buttons["확인했어요"].tap()
        XCTAssertTrue(app.navigationBars["활동 상세"].waitForExistence(timeout: 5))
    }
    private func capture(_ app: XCUIApplication, _ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name; shot.lifetime = .keepAlways; add(shot)
    }
}
