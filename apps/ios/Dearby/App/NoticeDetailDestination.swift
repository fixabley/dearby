import SwiftUI

/// App owns the OS action and presents failure on the active sheet/navigation destination.
struct NoticeDetailDestination: View {
    let notice: ActivityNotice
    let catalog: ActivityCatalog
    @Environment(\.openURL) private var openURL
    @State private var mapFailed = false
    @State private var calendarRequest: CalendarEditorRequest?
    @State private var calendarFailed = false

    var body: some View {
        let application = CalendarDraftMapper.application(notice, catalog: catalog)
        NoticeDetailView(notice: notice, catalog: catalog,
                         onAddApplication: application.map { draft in { openCalendar(draft) } }, onOpenMap: openMap)
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
        guard let request = CalendarEditorRequest(draft: draft) else { calendarFailed = true; return }
        calendarRequest = request
    }

    private func openMap(_ venue: ActivityVenue) {
        VenueMapLauncher.open(venue, using: { url, completion in
            openURL(url, completion: completion)
        }, onFailure: { mapFailed = true })
    }
}
