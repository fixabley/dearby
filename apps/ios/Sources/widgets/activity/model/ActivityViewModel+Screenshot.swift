#if DEBUG
import Foundation

extension ActivityViewModel {
    /// Store screenshots (`ScreenshotScene`): waits for the first program page, expands the first program and returns its first open
    /// (else upcoming) activity once loaded; nil after 30 seconds or when there is none.
    func screenshotActivity() async -> String? {
        for _ in 0..<150 {
            if phase == .loaded, let program = programs.first {
                if !isExpanded(program.id) { toggle(program.id) }
                if let open = openActivities[program.id], let upcoming = upcomingActivities[program.id] { return (open + upcoming).first?.id }
            }
            try? await Task.sleep(for: .milliseconds(200))
        }
        return nil
    }
}
#endif
