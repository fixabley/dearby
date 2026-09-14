import SwiftUI

/// App owns the OS action and presents failure on the active sheet/navigation destination.
struct NoticeDetailDestination: View {
    let detail: NoticeDetail
    @Environment(\.openURL) private var openURL
    @State private var mapFailed = false
    @State private var calendarRequest: CalendarEditorRequest?
    @State private var calendarFailed = false

    var body: some View {
        let application = CalendarDraftMapper.application(detail)
        let phases = detail.schedules.map {
            CalendarDraftMapper.schedule($0, detail: detail, mapURL: VenueMapLink.url)
        }
        NoticeDetailView(detail: detail,
                         onAddSchedule: phases.map { draft in draft.map { event in { openCalendar(event) } } },
                         onAddApplication: application.map { draft in { openCalendar(draft) } },
                         onOpenMap: openMap)
            .sheet(item: $calendarRequest) { request in
                CalendarEventEditor(request: request, onDismiss: { calendarRequest = nil })
            }
            .alert("캘린더를 열지 못했어요", isPresented: $calendarFailed) {
                Button("확인", role: .cancel) {}
            } message: { Text("날짜를 확인한 후 다시 시도해 주세요.") }
            .alert("지도를 열지 못했어요", isPresented: $mapFailed) {
                Button("확인", role: .cancel) {}
            } message: {
                Text("잠시 후 다시 시도해 주세요.")
            }
    }

    private func openCalendar(_ draft: CalendarEventDraft) {
        CalendarEditorRequest.prepare(draft, present: { calendarRequest = $0 },
                                      onFailure: { calendarFailed = true })
    }

    private func openMap(_ venue: NoticeVenue) {
        VenueMapLauncher.open(venue, using: { url, completion in
            openURL(url, completion: completion)
        }, onFailure: { mapFailed = true })
    }
}
