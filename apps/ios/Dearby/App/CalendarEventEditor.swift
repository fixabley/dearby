import SwiftUI
import EventKitUI

struct CalendarEventEditor: UIViewControllerRepresentable {
    let request: CalendarEditorRequest
    let onDismiss: () -> Void

    func makeCoordinator() -> CalendarEditorDelegate { CalendarEditorDelegate(onDismiss: onDismiss) }

    func makeUIViewController(context: Context) -> EKEventEditViewController {
        let controller = EKEventEditViewController()
        let store = EKEventStore()
        let event = EKEvent(eventStore: store)
        event.title = request.draft.title
        event.startDate = request.start
        event.endDate = request.end
        event.isAllDay = request.isAllDay
        event.timeZone = request.timezone
        event.location = request.draft.location
        event.url = request.draft.url
        event.notes = request.draft.notes
        controller.eventStore = store
        controller.event = event
        controller.editViewDelegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: EKEventEditViewController, context: Context) {}
}
