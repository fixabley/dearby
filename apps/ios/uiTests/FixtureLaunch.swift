import XCTest

extension XCUIApplication {
    /// Launches the Debug app against the in-process fixture server instead of a real API,
    /// with a session Keychain item of its own so earlier sign-ins never carry over.
    func launch(with server: FixtureServer) {
        launchEnvironment["DEARBY_API_ORIGIN"] = server.origin
        launchEnvironment["DEARBY_SESSION_SERVICE"] = launchEnvironment["DEARBY_SESSION_SERVICE"] ?? "dearby.uitest.\(UUID().uuidString)"
        launch()
    }
}
