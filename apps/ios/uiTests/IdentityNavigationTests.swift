import XCTest

@MainActor final class IdentityNavigationTests: XCTestCase {
    func testProfileGuestAndEditing() {
        let app = XCUIApplication()
        app.launch()
        app.buttons["tab-4"].tap()
        XCTAssertTrue(app.buttons["로그인하고 시작하기"].waitForExistence(timeout: 5))
        capture(app, "profile-guest")
        app.buttons["로그인하고 시작하기"].tap()
        XCTAssertTrue(app.buttons["편집"].waitForExistence(timeout: 5))
        capture(app, "profile")
        app.buttons["편집"].tap()
        XCTAssertTrue(app.navigationBars["프로필 편집"].waitForExistence(timeout: 5))
        app.buttons["완료"].tap()
        app.terminate(); app.launch(); app.buttons["tab-4"].tap()
        XCTAssertTrue(app.buttons["로그인하고 시작하기"].waitForExistence(timeout: 5))
    }
    func testQRSignedOutOffersCardCreation() {
        let app = XCUIApplication()
        app.launch(); app.buttons["tab-2"].tap()
        XCTAssertTrue(app.buttons["명함 만들기"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.images["명함 QR"].exists)
        capture(app, "qr-signed-out")
        app.buttons["명함 만들기"].tap()
        XCTAssertTrue(app.navigationBars["명함 만들기"].waitForExistence(timeout: 5))
        app.buttons["닫기"].firstMatch.tap()
        XCTAssertTrue(app.buttons["명함 만들기"].waitForExistence(timeout: 5))
    }
    func testScannedShareOpensTheCardWithItsActivitiesWithoutSignIn() throws {
        let server = try FixtureServer()
        let app = XCUIApplication()
        app.launchEnvironment["DEARBY_SCAN_TEXT"] = "\(XCUIApplication.debugWeb)/s/\(FixtureServer.shareID)"
        app.launch(with: server); app.buttons["tab-2"].tap(); app.buttons["QR 찍기"].tap()
        XCTAssertTrue(app.navigationBars["공유 명함"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["이서연"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["함께 공유된 활동"].exists)
        XCTAssertTrue(app.staticTexts["테스트 컨퍼런스"].exists)
        XCTAssertFalse(server.recorded().contains { $0.contains("/v1/auth") })
        capture(app, "received-share")
    }
    func testLegacyCardCodeAndForeignCodes() throws {
        let server = try FixtureServer()
        let app = XCUIApplication()
        app.launchEnvironment["DEARBY_SCAN_TEXT"] = "dearby://card/\(FixtureServer.sharedCardID)"
        app.launch(with: server); app.buttons["tab-2"].tap(); app.buttons["QR 찍기"].tap()
        XCTAssertTrue(app.staticTexts["이서연"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["함께 공유된 활동"].exists)
        app.terminate()
        app.launchEnvironment["DEARBY_SCAN_TEXT"] = "https://example.test/not-dearby"
        app.launch(with: server); app.buttons["tab-2"].tap(); app.buttons["QR 찍기"].tap()
        XCTAssertTrue(app.staticTexts["Dearby 명함 QR이 아니에요."].waitForExistence(timeout: 10))
    }
    func testComposerAsksSignInOnlyOnPublishThenPublishes() throws {
        let server = try FixtureServer()
        let app = XCUIApplication()
        app.launch(with: server); app.buttons["tab-2"].tap()
        let create = app.buttons["명함 만들기"]
        XCTAssertTrue(create.waitForExistence(timeout: 5))
        XCTAssertFalse(server.recorded().contains { $0.contains("/v1/cards") })
        create.tap()
        XCTAssertTrue(app.navigationBars["명함 만들기"].waitForExistence(timeout: 5))
        let publish = app.buttons["로그인하고 명함 발행"]
        XCTAssertFalse(publish.isEnabled)
        XCTAssertFalse(server.recorded().contains { $0.contains("/v1/auth") || $0.contains("/v1/profile") })
        app.textFields["이름"].tap(); app.textFields["이름"].typeText("김지민")
        capture(app, "card-composer")
        publish.tap()
        XCTAssertTrue(app.navigationBars["로그인"].waitForExistence(timeout: 5))
        app.textFields["sign-in-email"].tap(); app.textFields["sign-in-email"].typeText("me@example.test")
        app.buttons["인증번호 받기"].tap()
        let code = app.textFields["sign-in-code"]
        XCTAssertTrue(code.waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label ENDSWITH '초 후 다시 받을 수 있어요'")).firstMatch.exists)
        capture(app, "sign-in-code")
        code.tap(); code.typeText("000000"); app.buttons["로그인"].tap()
        XCTAssertTrue(app.staticTexts["인증번호가 맞지 않거나 만료됐어요."].waitForExistence(timeout: 5))
        code.tap(); code.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 6) + FixtureServer.code)
        app.buttons["로그인"].tap()
        XCTAssertTrue(app.staticTexts["명함을 발행했어요"].waitForExistence(timeout: 10))
        capture(app, "card-published")
        let calls = server.recorded().filter { $0 != "GET /v1/catalog" }
        XCTAssertEqual(calls, ["POST /v1/auth/challenges", "POST /v1/auth/sessions", "POST /v1/auth/sessions",
                               "GET /v1/profile", "PUT /v1/profile", "POST /v1/cards"])
        app.buttons["확인"].tap()
        // Back on the QR tab the new card is shared right away as <web>/s/<share ID>.
        let qr = app.images["명함 QR"]
        XCTAssertTrue(qr.waitForExistence(timeout: 10))
        XCTAssertTrue((qr.value as? String ?? "").hasSuffix("/s/\(FixtureServer.shareID)"))
        XCTAssertEqual(server.recorded().suffix(2), ["GET /v1/cards", "POST /v1/cards/c1000000-0000-4000-8000-000000000001/shares"])
        capture(app, "qr-share")
    }
    func testChoosingAnAppliedActivityMakesANewShare() throws {
        let server = try FixtureServer()
        let app = XCUIApplication()
        app.launch(with: server)
        let conference = app.buttons["activity-\(CatalogFixture.conference)"]
        XCTAssertTrue(conference.waitForExistence(timeout: 10)); conference.tap()
        let report = app.buttons["신청 상태 수정"]
        for _ in 0..<8 where !report.isHittable { app.swipeUp() }
        report.tap(); app.buttons["신청했어요"].tap()
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["tab-2"].tap(); app.buttons["명함 만들기"].tap()
        app.textFields["이름"].tap(); app.textFields["이름"].typeText("김지민")
        app.buttons["로그인하고 명함 발행"].tap()
        app.textFields["sign-in-email"].tap(); app.textFields["sign-in-email"].typeText("me@example.test")
        app.buttons["인증번호 받기"].tap()
        let code = app.textFields["sign-in-code"]
        XCTAssertTrue(code.waitForExistence(timeout: 5))
        code.tap(); code.typeText(FixtureServer.code); app.buttons["로그인"].tap()
        XCTAssertTrue(app.buttons["확인"].waitForExistence(timeout: 10)); app.buttons["확인"].tap()
        XCTAssertTrue(app.images["명함 QR"].waitForExistence(timeout: 10))
        let picker = app.buttons["함께 보낼 활동 (선택)"]
        for _ in 0..<4 where !picker.isHittable { app.swipeUp() }
        picker.tap()
        let chip = app.buttons["테스트 컨퍼런스"]
        XCTAssertTrue(chip.waitForExistence(timeout: 5)); chip.tap()
        let shares = NSPredicate { _, _ in server.recorded().filter { $0.hasSuffix("/shares") }.count == 2 }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: shares, object: nil)], timeout: 10), .completed)
        capture(app, "qr-share-activity")
        // Unchoosing returns to the first share without making another.
        chip.tap()
        Thread.sleep(forTimeInterval: 1)
        XCTAssertEqual(server.recorded().filter { $0.hasSuffix("/shares") }.count, 2)
    }
    func testCardFailureAfterProfileSaveRetriesOnlyTheCard() throws {
        let server = try FixtureServer()
        server.failingCards = true
        let app = XCUIApplication()
        app.launch(with: server); app.buttons["tab-2"].tap()
        XCTAssertTrue(app.buttons["명함 만들기"].waitForExistence(timeout: 5))
        app.buttons["명함 만들기"].tap()
        app.textFields["이름"].tap(); app.textFields["이름"].typeText("김지민")
        app.buttons["로그인하고 명함 발행"].tap()
        app.textFields["sign-in-email"].tap(); app.textFields["sign-in-email"].typeText("me@example.test")
        app.buttons["인증번호 받기"].tap()
        let code = app.textFields["sign-in-code"]
        XCTAssertTrue(code.waitForExistence(timeout: 5))
        code.tap(); code.typeText(FixtureServer.code); app.buttons["로그인"].tap()
        XCTAssertTrue(app.staticTexts["프로필은 저장했어요. 명함 발행만 다시 시도해 주세요."].waitForExistence(timeout: 10))
        server.failingCards = false
        app.buttons["명함 발행"].tap()
        XCTAssertTrue(app.staticTexts["명함을 발행했어요"].waitForExistence(timeout: 10))
        XCTAssertEqual(server.recorded().filter { $0 == "PUT /v1/profile" }.count, 1)
    }
    func testWalletSendPickerVisibilityAndSearch() {
        let app = XCUIApplication()
        app.launch(); app.buttons["tab-3"].tap()
        XCTAssertTrue(app.buttons["wallet-group-0"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["나도 명함 주기"].isHittable)
        capture(app, "wallet")
        app.buttons["나도 명함 주기"].tap()
        XCTAssertTrue(app.buttons["이 명함 보내기"].waitForExistence(timeout: 5))
        let detail = app.buttons["send-card-preview"]
        let visible = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hittable == true"), object: detail)
        XCTAssertEqual(XCTWaiter.wait(for: [visible], timeout: 5), .completed)
        capture(app, "send-card-picker")
        detail.tap()
        XCTAssertTrue(app.navigationBars["공유 카드"].waitForExistence(timeout: 5))
        app.buttons["닫기"].firstMatch.tap()
        app.buttons["이 명함 보내기"].tap(); app.alerts.buttons["확인"].tap()
        app.buttons["wallet-group-1"].tap(); capture(app, "wallet-reciprocal")
        let search = app.textFields["wallet-search"]
        search.tap(); search.typeText("없는사람")
        XCTAssertTrue(app.staticTexts["찾는 명함이 없어요"].waitForExistence(timeout: 5))
    }
    func testAllReciprocalWalletAndCardEditOpensComposer() {
        let app = XCUIApplication()
        app.launch(); app.buttons["tab-3"].tap()
        for _ in 0..<3 {
            let send = app.buttons["나도 명함 주기"]
            for _ in 0..<3 where !send.isHittable { app.swipeUp() }
            send.tap()
            XCTAssertTrue(app.buttons["이 명함 보내기"].waitForExistence(timeout: 5))
            app.buttons["이 명함 보내기"].tap(); app.alerts.buttons["확인"].tap()
        }
        XCTAssertTrue(app.buttons["wallet-group-0"].waitForNonExistence(timeout: 5))
        capture(app, "wallet-reciprocal-only")
        app.buttons["tab-2"].tap(); app.buttons["명함 만들기"].tap()
        XCTAssertTrue(app.navigationBars["명함 만들기"].waitForExistence(timeout: 5))
        app.buttons["닫기"].firstMatch.tap()
    }
    func testMyActivitiesStartEmptyThenListMarkedActivities() throws {
        let server = try FixtureServer()
        let app = XCUIApplication()
        let conference = "activity-\(CatalogFixture.conference)"
        app.launch(with: server); app.buttons["tab-1"].tap()
        XCTAssertTrue(app.staticTexts["신청한 활동이 없어요"].waitForExistence(timeout: 5))
        app.buttons["explore"].tap()
        XCTAssertTrue(app.buttons[conference].waitForExistence(timeout: 10))
        app.buttons[conference].tap()
        let report = app.buttons["신청 상태 수정"]
        for _ in 0..<8 where !report.isHittable { app.swipeUp() }
        report.tap(); app.buttons["신청했어요"].tap()
        let confirm = app.switches["confirm-\(CatalogFixture.conference)"]
        XCTAssertTrue(confirm.waitForExistence(timeout: 5))
        confirm.switches.firstMatch.tap()
        XCTAssertEqual(confirm.value as? String, "1")
        app.navigationBars.buttons.element(boundBy: 0).tap(); app.buttons["tab-1"].tap()
        XCTAssertTrue(app.buttons[conference].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["참여 확정"].exists)
        capture(app, "my-activities")
        app.terminate(); app.launch(with: server); app.buttons["tab-1"].tap()
        XCTAssertTrue(app.staticTexts["신청한 활동이 없어요"].waitForExistence(timeout: 5))
    }
    private func capture(_ app: XCUIApplication, _ name: String) {
        Thread.sleep(forTimeInterval: 0.6)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }
}
