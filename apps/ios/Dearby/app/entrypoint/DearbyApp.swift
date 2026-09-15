import SwiftUI

@main
struct DearbyApp: App {
    @State private var session = AppSession()

    var body: some Scene {
        WindowGroup {
            ContentView(session: session)
        }
    }
}
