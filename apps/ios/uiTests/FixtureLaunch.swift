import XCTest

extension XCUIApplication {
    /// Launches the Debug app against the in-process fixture server instead of a real API,
    /// with a session Keychain item of its own so earlier sign-ins never carry over.
    /// Debug builds without an injected web origin use this one for `/s/<id>` links.
    static let debugWeb = "http://localhost:3210"
    func launch(with server: FixtureServer) {
        launchEnvironment["DEARBY_API_ORIGIN"] = server.origin
        launchEnvironment["DEARBY_SESSION_SERVICE"] = launchEnvironment["DEARBY_SESSION_SERVICE"] ?? "dearby.uitest.\(UUID().uuidString)"
        launch()
    }
}
