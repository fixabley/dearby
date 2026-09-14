import EventKitUI

final class CalendarEditorDelegate: NSObject, EKEventEditViewDelegate {
    private let onDismiss: () -> Void
    init(onDismiss: @escaping () -> Void) { self.onDismiss = onDismiss }

    func eventEditViewController(_ controller: EKEventEditViewController, didCompleteWith action: EKEventEditViewAction) {
        // Dismiss for cancel/save/delete; never infer a save merely from presentation.
        onDismiss()
    }
}
