import XCTest

/// 재동의·로그인 시트·설정 (a, b, h).
final class AccountFlowTests: DearbyFlowTestCase {
    /// (a) The renewed privacy policy: its title, then 동의하고 계속 (done in `launch()`). Shown once per new policy version, so it is skipped
    /// once the account agreed — run this test first (`-only-testing:DearbyUITests/AccountFlowTests/testAConsentRenewal`).
    func testAConsentRenewal() throws {
        launch()
        if !agreedRenewal { throw XCTSkip("재동의 화면이 뜨지 않았어요(이미 현재 버전에 동의했거나 로그아웃 상태)") }
        snapshot("재동의 완료 뒤 발견")
    }

    /// (b) Signed out only: the sign-in sheet top to bottom — lockup, Apple, Google, the one-line notice, then the quiet passkey button.
    func testBSignInSheetLayout() throws {
        launch()
        if isSignedIn() { throw XCTSkip("로그인 상태라 로그인 시트를 건너뛰어요") }
        openTab("발견")
        openSettings()
        app.buttons["settings-sign-in"].tap()
        let lockup = app.images["Dearby, 대외활동, 매일 맞춤으로"]
        XCTAssertTrue(lockup.waitForExistence(timeout: 10), "락업 이미지가 없어요")
        snapshot("로그인 시트")
        let order = [lockup, app.buttons["sign-in-apple"], app.buttons["sign-in-google"],
                     app.descendants(matching: .any)["sign-in-privacy-notice"].firstMatch, app.buttons["sign-in-passkey"]]
        for element in order { XCTAssertTrue(element.exists, "\(element) 가 없어요") }
        let tops = order.map(\.frame.minY)
        XCTAssertEqual(tops, tops.sorted(), "락업·Apple·Google·고지·패스키 순서가 아니에요: \(tops)")
        app.navigationBars["로그인"].buttons["닫기"].tap()
    }

    /// (h) 설정: 알림 and 개인정보 sections and the 광고 목적 정보 제공 switch (looked at, never flipped).
    func testHSettingsSections() throws {
        launch()
        let signedIn = isSignedIn()
        openSettings()
        snapshot("설정 위")
        if signedIn {
            XCTAssertTrue(scroll(to: app.switches["settings-new-recruitment"]), "알림 섹션의 새 모집 알림 스위치가 없어요")
            XCTAssertTrue(app.staticTexts["알림"].exists, "알림 섹션 머리글이 없어요")
            snapshot("설정 알림")
        }
        XCTAssertTrue(scroll(to: app.switches["settings-usage-stats"]), "개인정보 섹션의 사용 통계 스위치가 없어요")
        XCTAssertTrue(app.staticTexts["개인정보"].exists, "개인정보 섹션 머리글이 없어요")
        if !signedIn { throw XCTSkip("로그아웃 상태라 알림·광고 목적 정보 제공을 건너뛰어요") }
        let adConsent = app.switches["settings-ad-consent"]
        XCTAssertTrue(scroll(to: adConsent), "광고 목적 정보 제공 스위치가 없어요")
        XCTAssertTrue(adConsent.staticTexts["광고 목적 정보 제공"].exists || app.staticTexts["광고 목적 정보 제공"].exists)
        snapshot("설정 개인정보")
    }
}
