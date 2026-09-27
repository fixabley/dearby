import XCTest

@MainActor final class CatalogNavigationTests: XCTestCase {
    func testActualCatalogSourceApplicationAndPersistence() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()
        if app.staticTexts["서버 미설정 · 저장된 정보는 유지됩니다"].waitForExistence(timeout: 1) {
            throw XCTSkip("Requires explicitly configured real local catalog API")
        }
        let activity = app.buttons.matching(identifier: "activity-ed43a1d2-213c-5511-bfe2-e80aa26cd866").firstMatch
        guard activity.waitForExistence(timeout: 25) else {
            XCTFail("Requires real API with current if(kakao)26; " + app.debugDescription)
            return
        }
        XCTAssertFalse(app.searchFields.firstMatch.exists)
        capture("실제 API 모집 중 발견")
        activity.tap()
        XCTAssertTrue(app.navigationBars["활동 상세"].waitForExistence(timeout: 5))
        capture("실제 활동 상세 기본 정보")
        scrollTo(app.buttons["신청 상태 수정"], in: app)
        app.buttons["신청 상태 수정"].tap()
        XCTAssertTrue(app.buttons["신청하지 않았어요"].waitForExistence(timeout: 5))
        app.buttons["신청하지 않았어요"].tap()
        scrollTo(app.buttons["프로그램 저장"], alternate: app.buttons["프로그램 저장됨 · 해제"], in: app)
        if app.buttons["프로그램 저장"].isHittable { app.buttons["프로그램 저장"].tap() }
        scrollTo(app.buttons["조직 저장"], alternate: app.buttons["조직 저장됨 · 해제"], in: app)
        if app.buttons["조직 저장"].exists { app.buttons["조직 저장"].tap() }
        capture("프로그램 조직 기기 저장")
        scrollTo(app.buttons["공식 사이트 보기"], in: app)
        app.buttons["공식 사이트 보기"].tap()
        closeBrowser(app)
        XCTAssertFalse(app.buttons["신청했어요"].waitForExistence(timeout: 1), "Source-only must not ask application status")
        scrollTo(app.buttons["신청 페이지 열기"], in: app)
        app.buttons["신청 페이지 열기"].tap()
        capture("외부 신청 앱 내 브라우저")
        closeBrowser(app)
        XCTAssertTrue(app.buttons["신청했어요"].waitForExistence(timeout: 5))
        capture("신청 여부 직접 기록")
        app.buttons["나중에"].tap()
        XCTAssertTrue(app.staticTexts["이 활동은 이미 신청한 활동이에요."].waitForNonExistence(timeout: 5))
        app.buttons["신청 페이지 열기"].tap()
        closeBrowser(app)
        XCTAssertTrue(app.buttons["신청했어요"].waitForExistence(timeout: 5))
        app.buttons["신청했어요"].tap()
        XCTAssertTrue(app.staticTexts["이 활동은 이미 신청한 활동이에요."].waitForExistence(timeout: 5), app.debugDescription)
        scrollTo(app.buttons["공식 사이트에서 확인하기"], in: app)
        XCTAssertTrue(app.buttons["공식 사이트에서 확인하기"].exists)
        capture("신청 기록 얇은 고정 안내")
        app.terminate()
        app.launch()
        app.buttons["tab-1"].tap()
        XCTAssertTrue(app.buttons["프로그램 저장 해제"].waitForExistence(timeout: 10))
        scrollTo(app.buttons["조직 저장 해제"], in: app)
        XCTAssertTrue(app.buttons["조직 저장 해제"].exists)
        capture("재시작 후 저장 프로그램 조직")
        let savedActivity = app.buttons.matching(identifier: "activity-ed43a1d2-213c-5511-bfe2-e80aa26cd866").firstMatch
        scrollTo(savedActivity, in: app)
        savedActivity.tap()
        XCTAssertTrue(app.staticTexts["이 활동은 이미 신청한 활동이에요."].waitForExistence(timeout: 5), app.debugDescription)
        capture("재시작 후 신청 기록 복원")
        scrollTo(app.buttons["신청 상태 수정"], in: app)
        app.buttons["신청 상태 수정"].tap()
        XCTAssertTrue(app.buttons["신청하지 않았어요"].waitForExistence(timeout: 5))
        app.buttons["신청하지 않았어요"].tap()
        XCTAssertTrue(app.staticTexts["이 활동은 이미 신청한 활동이에요."].waitForNonExistence(timeout: 5))
        capture("신청 기록 수정")
    }
    func testSourcePresentation() throws {
        let app = XCUIApplication()
        app.launch()
        if app.staticTexts["서버 미설정 · 저장된 정보는 유지됩니다"].waitForExistence(timeout: 1) {
            throw XCTSkip("Requires explicitly configured real local catalog API")
        }
        let activity = app.buttons["activity-ed43a1d2-213c-5511-bfe2-e80aa26cd866"].firstMatch
        XCTAssertTrue(activity.waitForExistence(timeout: 25))
        capture("실제 API 모집 중 발견")
        activity.tap()
        scrollTo(app.buttons["신청 상태 수정"], in: app)
        app.buttons["신청 상태 수정"].tap()
        XCTAssertTrue(app.buttons["신청했어요"].waitForExistence(timeout: 5))
        app.buttons["신청했어요"].tap()
        XCTAssertTrue(app.staticTexts["이 활동은 이미 신청한 활동이에요."].waitForExistence(timeout: 5))
        scrollTo(app.buttons["공식 사이트에서 확인하기"], in: app)
        capture("최종 공식 출처와 신청 자기기록 안내")
    }
    private func closeBrowser(_ app: XCUIApplication) {
        let close = app.buttons["닫기"]
        if close.waitForExistence(timeout: 5) { close.tap(); return }
        let done = app.buttons["Done"]
        let korean = app.buttons["완료"]
        if done.waitForExistence(timeout: 10) { done.tap() } else if korean.exists { korean.tap() } else {
            XCTFail("Native Safari dismissal not found: " + app.debugDescription)
        }
    }
    private func scrollTo(_ element: XCUIElement, alternate: XCUIElement? = nil, in app: XCUIApplication) {
        for _ in 0..<8 {
            if visible(element, in: app) || (alternate.map { visible($0, in: app) } ?? false) { return }
            app.swipeUp()
        }
        for _ in 0..<8 {
            if visible(element, in: app) || (alternate.map { visible($0, in: app) } ?? false) { return }
            app.swipeDown()
        }
        XCTFail("Control unavailable: " + element.description + "\n" + app.debugDescription)
    }
    private func visible(_ element: XCUIElement, in app: XCUIApplication) -> Bool {
        guard element.exists && element.isHittable else { return false }
        let pinned = ["프로그램 저장", "프로그램 저장됨 · 해제", "신청 페이지 열기", "공식 사이트에서 확인하기"]
        if pinned.contains(element.label) { return element.frame.minY >= 120 && element.frame.maxY < app.frame.height - 25 }
        let tab = app.buttons["tab-1"]
        let bottom = tab.exists && tab.isHittable ? tab.frame.minY : app.frame.height - 105
        return element.frame.minY >= 130 && element.frame.maxY <= bottom
    }
    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
