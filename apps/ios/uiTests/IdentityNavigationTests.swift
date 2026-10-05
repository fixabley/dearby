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
        XCTAssertTrue(app.buttons["로그인하고 시작하기"].exists)
    }
    func testQRSharingScanAndCardEditor() {
        let app = XCUIApplication()
        app.launch(); app.buttons["tab-2"].tap()
        XCTAssertTrue(app.buttons["QR 확대"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["새 명함"].isHittable)
        capture(app, "qr-show")
        app.buttons["QR 확대"].tap(); XCTAssertTrue(app.buttons["닫기"].waitForExistence(timeout: 5))
        capture(app, "qr-enlarged"); app.buttons["닫기"].tap()
        app.buttons["명함 공유"].tap()
        XCTAssertTrue(app.buttons["QR 이미지 저장"].waitForExistence(timeout: 5))
        capture(app, "qr-share-menu")
        app.buttons["링크 복사"].tap(); app.buttons["닫기"].tap()
        app.buttons["QR 찍기"].tap(); capture(app, "qr-scan")
        app.buttons["예시 QR 읽기"].tap()
        XCTAssertTrue(app.buttons["save-shared-card"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["나도 카드 주기"].isHittable)
        capture(app, "shared-card")
        app.buttons["save-shared-card"].tap()
        XCTAssertTrue(app.buttons["카드 저장됨"].exists)
        app.buttons["닫기"].tap(); app.buttons["QR 보여주기"].tap()
        let tile = app.buttons["새 명함"]
        for _ in 0..<4 where !tile.isHittable { app.swipeUp() }
        tile.tap(); capture(app, "qr-new-card")
        app.buttons["명함 만들기"].tap()
        XCTAssertTrue(app.navigationBars["직접 만들기"].waitForExistence(timeout: 5))
        capture(app, "card-editor")
        app.buttons["contact-email"].tap()
        let create = app.buttons["공유 카드 만들기"]
        XCTAssertTrue(create.isHittable); create.tap()
        XCTAssertTrue(app.buttons["QR 확대"].waitForExistence(timeout: 5))
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
    func testAllReciprocalWalletAndExistingCardEdit() {
        let app = XCUIApplication()
        app.launch(); app.buttons["tab-3"].tap()
        for _ in 0..<3 {
            let send = app.buttons["나도 명함 주기"]
            for _ in 0..<3 where !send.isHittable { app.swipeUp() }
            send.tap()
            XCTAssertTrue(app.buttons["이 명함 보내기"].waitForExistence(timeout: 5))
            app.buttons["이 명함 보내기"].tap(); app.alerts.buttons["확인"].tap()
        }
        XCTAssertFalse(app.buttons["wallet-group-0"].exists)
        capture(app, "wallet-reciprocal-only")
        app.buttons["tab-2"].tap(); app.buttons["명함 편집"].tap()
        XCTAssertTrue(app.buttons["수정 완료"].waitForExistence(timeout: 5))
        app.buttons["contact-email"].tap(); app.buttons["수정 완료"].tap()
        app.buttons["명함 편집"].tap()
        XCTAssertFalse(app.buttons["contact-email"].isSelected)
        app.buttons["닫기"].tap()
    }
    func testMyActivitiesConfirmMarkAndEmptyState() {
        let app = XCUIApplication()
        app.launch(); app.buttons["tab-1"].tap()
        XCTAssertTrue(app.buttons["activity-conference"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["신청함"].exists)
        let confirm = app.switches["confirm-conference"]
        XCTAssertEqual(confirm.value as? String, "0")
        confirm.switches.firstMatch.tap()
        XCTAssertTrue(app.staticTexts["참여 확정"].waitForExistence(timeout: 5))
        capture(app, "my-activities")
        app.buttons["activity-conference"].tap()
        XCTAssertTrue(app.navigationBars["활동 상세"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.switches["confirm-conference"].value as? String, "1")
        let report = app.buttons["신청 상태 수정"]
        for _ in 0..<8 where !report.isHittable { app.swipeUp() }
        capture(app, "activity-detail-bottom")
        report.tap(); app.buttons["신청하지 않았어요"].tap()
        XCTAssertTrue(app.switches["confirm-conference"].waitForNonExistence(timeout: 5))
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.staticTexts["신청한 활동이 없어요"].waitForExistence(timeout: 5))
        app.buttons["explore"].tap()
        XCTAssertTrue(app.staticTexts["활동 둘러보기"].waitForExistence(timeout: 5))
        app.terminate(); app.launch(); app.buttons["tab-1"].tap()
        XCTAssertTrue(app.buttons["activity-conference"].waitForExistence(timeout: 5))
    }
    private func capture(_ app: XCUIApplication, _ name: String) {
        Thread.sleep(forTimeInterval: 0.6)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }
}
