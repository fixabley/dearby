import Foundation

/// What `캘린더에 추가` prefills in the system's new-event screen (contract "내 캘린더에 추가" 2026-10-22). The app never writes the
/// event itself; the user saves or cancels in that screen.
struct CalendarEventDraft: Identifiable, Equatable {
    let id = UUID()
    /// "활동명 · 일정 제목".
    let title: String
    let start: Date
    let end: Date
    let allDay: Bool
    let timeZone: TimeZone?
    let location: String?
    /// The activity's page on the build's web origin (`ActivityViewModel.calendarLink`), set as the event URL and in the notes so the overlap check skips it.
    let url: URL
    /// Written into the notes after the activity link.
    let officialURL: URL?
    /// The schedule's repeat rule: the event repeats from its first occurrence (contract "반복 일정" 2026-10-22).
    var recurrence: ScheduleRecurrence? = nil
    var notes: String { ([url] + [officialURL].compactMap { $0 }).map(\.absoluteString).joined(separator: "\n") }
}
