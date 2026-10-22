import EventKit
import EventKitUI
import SwiftUI

/// The system's new-event screen, prefilled (contract "내 캘린더에 추가" 2026-10-22). SwiftUI has no equivalent, so this is the one UIKit
/// bridge. From iOS 17 the controller runs out of process without calendar access: nothing is requested and no event is read.
struct AddToCalendarSheet: UIViewControllerRepresentable {
    let draft: CalendarEventDraft
    @Environment(\.dismiss) private var dismiss
    func makeCoordinator() -> Coordinator { Coordinator { dismiss() } }
    func makeUIViewController(context: Context) -> EKEventEditViewController {
        let store = EKEventStore()
        let event = EKEvent(eventStore: store)
        event.title = draft.title
        event.startDate = draft.start
        event.endDate = draft.end
        event.isAllDay = draft.allDay
        event.timeZone = draft.timeZone
        event.location = draft.location
        event.url = draft.url
        event.notes = draft.url?.absoluteString
        let controller = EKEventEditViewController()
        controller.eventStore = store
        controller.event = event
        controller.editViewDelegate = context.coordinator
        return controller
    }
    func updateUIViewController(_ controller: EKEventEditViewController, context: Context) {}
    final class Coordinator: NSObject, EKEventEditViewDelegate {
        let done: () -> Void
        init(done: @escaping () -> Void) { self.done = done }
        func eventEditViewController(_ controller: EKEventEditViewController, didCompleteWith action: EKEventEditViewAction) { done() }
    }
}
