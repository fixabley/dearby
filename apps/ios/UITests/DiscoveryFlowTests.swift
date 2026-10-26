import XCTest

/// 발견의 맞춤 대기열, 즐겨찾기, 활동 상세의 겹치는 시간 확인 (e, f, g).
final class DiscoveryFlowTests: DearbyFlowTestCase {
    /// (e) 오늘의 맞춤 대기열 → page through to the end (광고 동의는 `동의하지 않음`, ATT는 추적 금지 요청; the ad is never tapped) → 닫기.
    func testEDiscoveryQueue() throws {
        launch()
        try requireSignedIn()
        openTab("발견")
        let entry = app.buttons["reco-queue"]
        XCTAssertTrue(scroll(to: entry), "오늘의 맞춤 대기열 줄이 없어요")
        entry.tap()
        let card = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS '오늘의 맞춤 대기열 '")).firstMatch
        let end = app.staticTexts["오늘 대기열을 다 봤어요"]
        let empty = app.staticTexts["아직 대기열이 없어요"]
        guard card.waitForExistence(timeout: 20) || end.exists || empty.exists else {
            snapshot("대기열 대신 열린 화면")
            throw XCTSkip("대기열 대신 다른 화면(추천 설정 퍼널 등)이 열렸어요")
        }
        snapshot("대기열 첫 장")
        declineAdConsentIfAsked()
        var page = 1
        while !end.exists && !empty.exists && page < 40 {
            app.swipeUp()
            page += 1
            declineAdConsentIfAsked()
            answerSystemAlert(timeout: 1)
            if page <= 3 { snapshot("대기열 \(page)쪽") }
        }
        XCTAssertTrue(end.exists || empty.exists, "40쪽을 넘겨도 대기열 끝이 나오지 않았어요")
        snapshot("대기열 끝")
        app.buttons["닫기"].firstMatch.tap()
        XCTAssertTrue(entry.waitForExistence(timeout: 10), "닫은 뒤 발견으로 돌아오지 않았어요")
    }

    /// (f) 즐겨찾기: only the calendar (no 목록 view to switch to), then the `날짜 없는 즐겨찾기` group.
    func testFFavoritesCalendarOnly() throws {
        launch()
        try requireSignedIn()
        openTab("내 활동")
        let segment = app.segmentedControls["mine-segment"]
        XCTAssertTrue(segment.waitForExistence(timeout: 10), "내 활동 | 즐겨찾기 전환이 없어요")
        segment.buttons["즐겨찾기"].tap()
        if app.staticTexts["하트를 누르면 여기에 모여요"].waitForExistence(timeout: 8) {
            snapshot("즐겨찾기 비어 있음")
            throw XCTSkip("즐겨찾기가 비어 있어요")
        }
        snapshot("즐겨찾기 캘린더")
        XCTAssertEqual(app.segmentedControls.count, 1, "즐겨찾기에 캘린더·목록 전환이 남아 있어요")
        XCTAssertFalse(app.buttons["목록"].exists, "즐겨찾기에 목록 보기가 남아 있어요")
        let undated = app.staticTexts["날짜 없는 즐겨찾기"]
        XCTAssertTrue(scroll(to: undated, limit: 15), "날짜 없는 즐겨찾기 묶음이 없어요(프로그램이나 날짜 없는 활동 즐겨찾기가 있어야 해요)")
        snapshot("날짜 없는 즐겨찾기")
    }

    /// (g) An activity detail › 겹치는 시간 확인하기 › 캘린더 읽기 허용하고 확인 (the calendar prompt is allowed) › the result.
    func testGCalendarOverlap() throws {
        launch()
        openTab("발견")
        let check = app.buttons["check-calendar"]
        let activities = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'activity-' AND NOT identifier IN %@",
                                                          ["activity-open-only", "activity-refresh-failed", "activity-more-retry", "activity-scroll-top"]))
        let programs = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'program-toggle-'"))
        XCTAssertTrue(programs.firstMatch.waitForExistence(timeout: 20), "발견에 프로그램이 없어요")
        var found = false
        for index in 0..<4 where !found {
            let program = programs.element(boundBy: index)
            guard program.exists else { break }
            if !program.isHittable { _ = scroll(to: program, limit: 3) }
            program.tap()
            let activity = activities.firstMatch
            guard activity.waitForExistence(timeout: 5) else {
                snapshot("펼친 프로그램 \(index + 1)에 활동 없음")
                continue
            }
            if !activity.isHittable { _ = scroll(to: activity, limit: 3) }
            activity.tap()
            found = scroll(to: check)
            snapshot(found ? "활동 상세" : "겹침 확인 없는 활동 상세 \(index + 1)")
            if !found {
                app.navigationBars.buttons.firstMatch.tap()
                program.tap()
            }
        }
        guard found else { throw XCTSkip("앞의 프로그램 4개에 시간이 정해진 일정이 있는 활동이 없어요") }
        // The detail's bottom bar (`공식 사이트에서 신청` and its variants) covers the button while XCUITest still calls it hittable:
        // scroll it clear of the bar.
        let bar = app.descendants(matching: .any)["open-application"].firstMatch
        for _ in 0..<4 where bar.exists && check.frame.maxY > bar.frame.minY - 8 { app.swipeUp() }
        check.tap()
        let read = app.buttons["캘린더 읽기 허용하고 확인"]
        XCTAssertTrue(read.waitForExistence(timeout: 10), "겹치는 시간 확인하기 안내가 열리지 않았어요")
        snapshot("겹침 확인 안내")
        read.tap()
        answerSystemAlert(timeout: 5)
        let compared = app.navigationBars["겹치는 시간"]
        let clear = app.staticTexts["기기 캘린더와 겹치는 시간이 없어요"]
        let denied = app.staticTexts["캘린더 읽기가 허용되지 않았어요"]
        XCTAssertTrue(compared.waitForExistence(timeout: 15) || clear.exists || denied.exists, "비교 결과가 나오지 않았어요")
        snapshot("겹침 결과")
        XCTAssertFalse(denied.exists, "캘린더 권한이 거부돼 있어요(설정 > Dearby > 캘린더)")
    }

    private func declineAdConsentIfAsked() {
        let decline = app.buttons["ad-consent-decline"]
        guard decline.waitForExistence(timeout: 1) else { return }
        snapshot("광고 동의 시트")
        decline.tap()
    }
}
