import XCTest
@testable import Dearby

final class WalletGroupTests: XCTestCase {
    private let activities = DemoActivities.all.sorted { $0.schedules[0].start < $1.schedules[0].start }.map { (id: $0.id, title: $0.title) }
    private var pending: [CardModel] { DemoIdentity.received.filter { !["received-3", "received-4"].contains($0.id) } }
    private func groups(_ query: String = "", collapsed: Set<String> = []) -> [WalletGroup] {
        WalletGroup.make(cards: pending, activityIDs: DemoIdentity.receivedActivityIDs, activities: activities, query: query, collapsed: collapsed)
    }
    private func ids(_ groups: [WalletGroup]) -> [String: [String]] {
        Dictionary(uniqueKeysWithValues: groups.map { ($0.id, $0.items.map(\.card.id)) })
    }

    func testSearchNeedsEveryTermInSomeFieldIgnoringCaseAndSpaces() {
        let fields = ["최유진", "Product Designer", "Dearby 개발자 컨퍼런스"]
        XCTAssertTrue(SearchQuery.matches("", fields: fields))
        XCTAssertTrue(SearchQuery.matches("   ", fields: fields))
        XCTAssertTrue(SearchQuery.matches("  유진  designer ", fields: fields))
        XCTAssertTrue(SearchQuery.matches("PRODUCT 컨퍼", fields: fields))
        XCTAssertFalse(SearchQuery.matches("유진 기획", fields: fields))
    }
    func testFixturesCoverTwoActivitiesAndNone() {
        XCTAssertTrue(DemoIdentity.receivedActivityIDs.values.contains { $0.count == 2 })
        XCTAssertTrue(DemoIdentity.received.contains { DemoIdentity.receivedActivityIDs[$0.id] == nil })
        XCTAssertEqual(activities.map(\.id), ["conference", "camp", "meetup"])
    }
    func testCardsRepeatInEveryActivityAndNoActivityComesLast() {
        let result = groups()
        XCTAssertEqual(result.map(\.id), ["conference", "camp", WalletGroup.noActivityID])
        XCTAssertEqual(ids(result)["conference"], ["received-0", "received-1"])
        XCTAssertEqual(ids(result)["camp"], ["received-0"])
        XCTAssertEqual(ids(result)[WalletGroup.noActivityID], ["received-2"])
        XCTAssertEqual(result.last?.title, "활동 없음")
        let keys = result.flatMap { $0.items.map(\.id) }
        XCTAssertEqual(keys.count, Set(keys).count)
    }
    func testSearchHidesEmptyGroupsAndShowsMatchesExpanded() {
        let result = groups("유진", collapsed: ["camp"])
        XCTAssertEqual(result.map(\.id), ["conference", "camp"])
        XCTAssertTrue(result.allSatisfy(\.expanded))
        XCTAssertEqual(groups(collapsed: ["camp"]).first { $0.id == "camp" }?.expanded, false)
        XCTAssertTrue(groups("없는사람").isEmpty)
    }
}
