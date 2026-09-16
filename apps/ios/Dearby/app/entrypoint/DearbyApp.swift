import SwiftUI

@main
struct DearbyApp: App {
    @State private var state = AppState()

    var body: some Scene {
        WindowGroup {
            ContentView(state: state)
        }
    }
}
