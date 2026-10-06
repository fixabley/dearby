import XCTest

extension XCUIApplication {
    /// Launches the Debug app against the in-process fixture server instead of a real API.
    func launch(with server: FixtureServer) {
        launchEnvironment["DEARBY_API_ORIGIN"] = server.origin
        launch()
    }
}
