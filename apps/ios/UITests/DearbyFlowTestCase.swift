import XCTest

/// UI flow tests on a real iPhone (2026-10-26 사용자 결정, README "UI 흐름 테스트"): not in CI, run by hand with `xcodebuild test -scheme DearbyUITests`.
/// They use the app as it is on the device — its sign-in state and data. Nothing here signs in, deletes, reports, blocks, clicks an ad or saves a
/// profile or card; a flow that needs another state (signed in, a card, a favorite) is skipped with the reason.
@MainActor
class DearbyFlowTestCase: XCTestCase {
    let app = XCUIApplication()
    let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    /// Set by `launch()` when the renewed-policy consent sheet was shown and agreed in this launch.
    private(set) var agreedRenewal = false

    override func setUp() async throws {
        continueAfterFailure = false
        // Permission prompts the flows may raise: 캘린더 (allow) and App Tracking Transparency (ask not to track).
        addUIInterruptionMonitor(withDescription: "System permission") { alert in
            Self.answer(alert)
        }
    }

    /// Launches the app (relaunching it) and, when the renewed privacy policy is asked, records it and agrees.
    func launch() {
        app.launch()
        XCTAssertTrue(app.tabBars.buttons["발견"].waitForExistence(timeout: 20), "탭 막대가 보이지 않아요")
        let renewal = app.staticTexts["개인정보 처리방침이 바뀌었어요"]
        guard renewal.waitForExistence(timeout: 4) else { return }
        snapshot("재동의 화면")
        for label in ["개인정보 수집·이용에 동의합니다, 필수", "이용약관에 동의합니다, 필수"] {
            app.descendants(matching: .any)[label].firstMatch.tap()
        }
        let agree = app.buttons["consent-continue"]
        XCTAssertTrue(agree.isEnabled, "두 항목을 고른 뒤에도 동의하고 계속이 꺼져 있어요")
        agree.tap()
        XCTAssertTrue(renewal.waitForNonExistence(timeout: 15), "동의한 뒤에도 재동의 화면이 남아 있어요")
        snapshot("재동의 뒤")
        agreedRenewal = true
    }

    func snapshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func openTab(_ title: String) {
        let tab = app.tabBars.buttons[title]
        XCTAssertTrue(tab.waitForExistence(timeout: 10), "\(title) 탭이 없어요")
        tab.tap()
    }

    /// 설정 from the current tab's navigation bar.
    func openSettings() {
        let settings = app.buttons["page-settings"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 10), "설정 버튼이 없어요")
        settings.tap()
        XCTAssertTrue(app.navigationBars["설정"].waitForExistence(timeout: 10), "설정 화면이 열리지 않았어요")
    }

    /// Signed in when 설정 shows 로그아웃; leaves 설정 again.
    func isSignedIn() -> Bool {
        openTab("발견")
        openSettings()
        let signOut = app.buttons["settings-sign-out"]
        let signIn = app.buttons["settings-sign-in"]
        _ = signOut.waitForExistence(timeout: 8) || signIn.waitForExistence(timeout: 2)
        let signedIn = signOut.exists
        app.navigationBars["설정"].buttons.firstMatch.tap()
        return signedIn
    }

    func requireSignedIn() throws {
        if !isSignedIn() { throw XCTSkip("로그아웃 상태라 계정 화면을 건너뛰어요") }
    }

    /// Swipes up until `element` is hittable (or `limit` swipes).
    @discardableResult
    func scroll(to element: XCUIElement, limit: Int = 8) -> Bool {
        for _ in 0..<limit where !(element.exists && element.isHittable) { app.swipeUp() }
        return element.exists && element.isHittable
    }

    /// Answers a system prompt that may be on screen now (springboard alerts don't always reach the interruption monitor).
    @discardableResult
    func answerSystemAlert(timeout: TimeInterval = 2) -> Bool {
        let alert = springboard.alerts.firstMatch
        guard alert.waitForExistence(timeout: timeout) else { return false }
        snapshot("시스템 권한 창")
        return Self.answer(alert)
    }

    /// 캘린더: allow (full access); App Tracking Transparency: ask not to track.
    @discardableResult
    static func answer(_ alert: XCUIElement) -> Bool {
        let notTrack = NSPredicate(format: "label CONTAINS '추적' AND (label CONTAINS '금지' OR label CONTAINS '않도록') OR label CONTAINS[c] 'Not to Track'")
        let allow = NSPredicate(format: "(label CONTAINS '허용' AND NOT label CONTAINS '안 함') OR label BEGINSWITH 'Allow'")
        for predicate in [notTrack, allow] {
            let button = alert.buttons.matching(predicate).firstMatch
            if button.exists { button.tap(); return true }
        }
        return false
    }
}
