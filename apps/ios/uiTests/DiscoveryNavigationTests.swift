import XCTest

@MainActor final class DiscoveryNavigationTests: XCTestCase {
    private let conference = "activity-\(CatalogFixture.conference)"
    private let camp = "activity-\(CatalogFixture.camp)"
    func testDiscoveryShowsOnlyOpenActivitiesFiltersAndQuickApply() throws {
        let server = try FixtureServer()
        let app = XCUIApplication()
        app.launch(with: server)
        XCTAssertTrue(app.buttons[conference].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons[camp].exists)
        XCTAssertFalse(app.buttons["activity-\(CatalogFixture.scheduled)"].exists)
        XCTAssertFalse(app.buttons["activity-\(CatalogFixture.stale)"].exists)
        // Quick apply only for the open registration activity.
        XCTAssertTrue(link(app, "apply-\(CatalogFixture.conference)").exists)
        XCTAssertFalse(link(app, "apply-\(CatalogFixture.camp)").exists)
        for index in 0...4 { XCTAssertTrue(app.buttons["tab-\(index)"].exists) }
        capture(app, "catalog-discovery")
        app.buttons["선발형"].tap()
        XCTAssertTrue(app.buttons[conference].waitForNonExistence(timeout: 5))
        XCTAssertTrue(app.buttons[camp].exists)
        app.buttons["바로 신청"].tap()
        XCTAssertTrue(app.buttons[camp].waitForNonExistence(timeout: 5))
        XCTAssertTrue(app.buttons[conference].waitForExistence(timeout: 5))
    }
    func testCatalogFailureShowsRetryAndNoExamples() throws {
        let server = try FixtureServer()
        server.failing = true
        let app = XCUIApplication()
        app.launch(with: server)
        XCTAssertTrue(app.staticTexts["활동을 불러오지 못했어요"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["activity-conference"].exists)
        XCTAssertFalse(app.buttons[conference].exists)
        capture(app, "catalog-error")
        server.failing = false
        app.buttons["catalog-retry"].tap()
        XCTAssertTrue(app.buttons[conference].waitForExistence(timeout: 10))
    }
    func testDetailLinksToOfficialApplicationAndKeepsTheMarkLocal() throws {
        let server = try FixtureServer()
        let app = XCUIApplication()
        app.launch(with: server)
        XCTAssertTrue(app.buttons[conference].waitForExistence(timeout: 10))
        app.buttons[conference].tap()
        XCTAssertTrue(app.navigationBars["활동 상세"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["테스트 주최"].exists)
        XCTAssertTrue(link(app, "open-application").waitForExistence(timeout: 5))
        XCTAssertEqual(link(app, "open-application").label, "공식 사이트에서 신청")
        capture(app, "catalog-detail")
        let report = app.buttons["신청 상태 수정"]
        for _ in 0..<8 where !report.isHittable { app.swipeUp() }
        report.tap(); app.buttons["신청했어요"].tap()
        XCTAssertTrue(app.staticTexts["신청했다고 표시했어요 · 실제 접수 확인이 아니에요"].waitForExistence(timeout: 5))
        app.terminate()
        app.launch(with: server)
        XCTAssertTrue(app.buttons[conference].waitForExistence(timeout: 10))
        app.buttons[conference].tap()
        XCTAssertTrue(app.navigationBars["활동 상세"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["신청했다고 표시했어요 · 실제 접수 확인이 아니에요"].exists)
    }
    func testCalendarExampleWithAndWithoutOverlap() throws {
        let server = try FixtureServer()
        let app = XCUIApplication()
        for id in [CatalogFixture.conference, CatalogFixture.camp] {
            app.launch(with: server)
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
            let result = id == CatalogFixture.conference ? "60분이 겹쳐요" : "예시 캘린더와 겹치는 시간이 없어요"
            XCTAssertTrue(app.staticTexts[result].waitForExistence(timeout: 5))
            XCTAssertTrue(app.buttons["선택한 캘린더로 확인"].waitForNonExistence(timeout: 5))
            XCTAssertTrue(app.switches["예시 캘린더"].waitForNonExistence(timeout: 5))
            if id == CatalogFixture.conference { XCTAssertTrue(app.staticTexts["1 / 1"].exists) }
            // Sheet detent animation can still be drawing after accessibility settles.
            Thread.sleep(forTimeInterval: 0.6)
            capture(app, "timeline-calendar-\(id)")
            app.buttons["확인했어요"].tap()
            XCTAssertTrue(app.navigationBars["활동 상세"].waitForExistence(timeout: 5))
            app.terminate()
        }
    }
    func testAccessibilityTextCalendarAndWalletRemainUsable() throws {
        let server = try FixtureServer()
        let app = XCUIApplication()
        app.launchArguments = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launch(with: server)
        XCTAssertTrue(app.buttons[conference].waitForExistence(timeout: 10)); app.buttons[conference].tap()
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
    /// SwiftUI `Link` is a button on iOS 26 and a link on iOS 27, so match the identifier on any element type.
    private func link(_ app: XCUIApplication, _ id: String) -> XCUIElement { app.descendants(matching: .any)[id].firstMatch }
    private func capture(_ app: XCUIApplication, _ name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = name; shot.lifetime = .keepAlways; add(shot)
    }
}
