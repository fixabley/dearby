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
    let url: URL?
}
