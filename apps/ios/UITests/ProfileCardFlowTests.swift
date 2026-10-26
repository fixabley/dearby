import XCTest

/// 프로필 보기·수정과 명함 만들기·QR (c, d). Nothing is saved or published: the edit is thrown away and the composer closed.
final class ProfileCardFlowTests: DearbyFlowTestCase {
    /// (c) Read mode as a card face with the 소개 링크 button and history rows; the editor scrolled to the bottom without a keyboard; 연락처 추가
    /// with its icons and a GitHub row that keeps only the username; then 완료 → 버리기.
    func testCProfileReadAndEdit() throws {
        launch()
        try requireSignedIn()
        openTab("내 프로필")
        let edit = app.buttons["profile-edit"]
        guard edit.waitForExistence(timeout: 15) else { throw XCTSkip("저장된 프로필이 없어 첫 프로필 퍼널이 보여요") }
        XCTAssertTrue(app.staticTexts["활동 이력"].waitForExistence(timeout: 10), "읽기 모드의 활동 이력 머리글이 없어요")
        XCTAssertTrue(app.buttons["소개 링크 열기"].exists, "명함형 카드의 소개 링크 버튼이 없어요(프로필에 소개 링크가 있어야 해요)")
        snapshot("프로필 보기 위")
        app.swipeUp()
        snapshot("프로필 보기 이력")

        edit.tap()
        let addContact = app.buttons["연락처 추가"]
        XCTAssertTrue(addContact.waitForExistence(timeout: 10), "수정 모드가 열리지 않았어요")
        snapshot("프로필 수정 위")
        for _ in 0..<8 { app.swipeUp() }
        XCTAssertEqual(app.keyboards.count, 0, "맨 아래로 스크롤했는데 키보드가 떠 있어요")
        snapshot("프로필 수정 맨 아래")

        for _ in 0..<8 where !addContact.isHittable { app.swipeDown() }
        addContact.tap()
        XCTAssertTrue(app.navigationBars["추가할 연락처"].waitForExistence(timeout: 5), "연락처 추가 목록이 열리지 않았어요")
        for kind in ["카카오톡", "Instagram", "GitHub", "Behance"] {
            XCTAssertTrue(app.buttons[kind].exists, "연락처 추가 목록에 \(kind)가 없어요")
        }
        snapshot("연락처 추가 목록")
        app.buttons["GitHub"].tap()
        let github = app.textFields["GitHub"]
        XCTAssertTrue(github.waitForExistence(timeout: 5), "GitHub 입력 칸이 생기지 않았어요")
        github.tap()
        github.typeText("https://github.com/dearby-uitest")
        XCTAssertEqual(github.value as? String, "dearby-uitest", "GitHub 칸은 주소 대신 사용자 이름만 남겨야 해요")
        snapshot("GitHub 접두어 입력")

        // 완료 with a change asks 저장 / 버리기 / 취소: throw the edit away.
        edit.tap()
        let discard = app.buttons["버리기"]
        XCTAssertTrue(discard.waitForExistence(timeout: 5), "바뀐 내용을 저장할까요? 확인이 뜨지 않았어요")
        discard.tap()
        XCTAssertTrue(app.staticTexts["활동 이력"].waitForExistence(timeout: 10), "버린 뒤 읽기 모드로 돌아오지 않았어요")
        XCTAssertFalse(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'dearby-uitest'")).firstMatch.exists, "버린 연락처가 남아 있어요")
        snapshot("수정 취소 뒤")
    }

    /// (d) 명함 만들기 opens with the 사진 넣기 / 소개 링크 넣기 rows; 내 명함's QR 보이기 shows a code. Never published.
    func testDCardComposerAndQR() throws {
        launch()
        try requireSignedIn()
        openTab("명함")
        app.segmentedControls["cards-segment"].buttons["내 명함"].tap()
        let newCard = app.buttons["새 명함"]
        let firstCard = app.buttons["명함 만들기"]
        XCTAssertTrue(newCard.waitForExistence(timeout: 15) || firstCard.exists, "명함 목록이 열리지 않았어요")
        let hasCards = newCard.exists
        snapshot("내 명함")
        (hasCards ? newCard : firstCard).tap()
        XCTAssertTrue(app.navigationBars["명함 만들기"].waitForExistence(timeout: 10), "명함 만들기 화면이 열리지 않았어요")
        let photo = app.descendants(matching: .any)["card-include-photo"].firstMatch
        XCTAssertTrue(photo.exists || app.staticTexts["프로필에서 사진을 넣을 수 있어요"].exists, "사진 구역이 없어요")
        XCTAssertTrue(photo.exists, "사진 넣기 토글이 없어요(프로필 사진이 있어야 해요)")
        XCTAssertTrue(app.descendants(matching: .any)["card-include-intro-link"].firstMatch.exists, "소개 링크 넣기 토글이 없어요(프로필에 소개 링크가 있어야 해요)")
        snapshot("명함 만들기")
        app.navigationBars["명함 만들기"].buttons["닫기"].tap()

        guard hasCards else { throw XCTSkip("만든 명함이 없어 QR 보이기를 건너뛰어요") }
        let showQR = app.buttons["QR 보이기"]
        XCTAssertTrue(scroll(to: showQR), "QR 보이기 버튼이 없어요")
        showQR.tap()
        let refresh = app.buttons["QR 새로 만들기"]
        XCTAssertTrue(refresh.waitForExistence(timeout: 10), "QR 화면이 열리지 않았어요")
        let issued = XCTNSPredicateExpectation(predicate: NSPredicate(format: "isEnabled == true"), object: refresh)
        XCTAssertEqual(XCTWaiter.wait(for: [issued], timeout: 15), .completed, "QR을 받아 오지 못했어요")
        snapshot("QR 보이기")
        app.navigationBars["QR 보이기"].buttons["닫기"].tap()
    }
}
