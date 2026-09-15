import SwiftUI
import EventKit

/// App owns the OS action and presents failure on the active sheet/navigation destination.
struct NoticeDetailDestination: View {
    let state: NoticeDetailState
    let notice: NoticeModel
    let calendarPreferences: CalendarPreferences
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @State private var busyCalendar: BusyCalendarSession
    @State private var mapFailed = false
    @State private var calendarRequest: CalendarEditorRequest?
    @State private var calendarFailed = false

    init(state: NoticeDetailState, notice: NoticeModel, calendarPreferences: CalendarPreferences) {
        self.state = state; self.notice = notice; self.calendarPreferences = calendarPreferences
        _busyCalendar = State(initialValue: BusyCalendarSession(provider: calendarPreferences.provider))
    }

    var body: some View {
        let application = CalendarDraftMapper.application(notice)
        let phases = notice.schedules.map {
            CalendarDraftMapper.schedule($0, detail: notice)
        }
        NoticeDetailView(state: state,
                         onAddSchedule: phases.map { draft in draft.map { event in { openCalendar(event) } } },
                         onAddApplication: application.map { draft in { openCalendar(draft) } },
                         onOpenMap: openMap,
                         busyDays: busyCalendar.days,
                         onSelectActivityDay: { index, day in
                             busyCalendar.select(id: index, day: DateInterval(start: day.start, end: day.end),
                                 activity: DateInterval(start: day.clippedStart, end: day.clippedEnd))
                         }, onRetryBusy: busyCalendar.refresh)
            .onAppear { calendarPreferences.attach(busyCalendar) }
            .onDisappear { calendarPreferences.detach(busyCalendar) }
            .onChange(of: scenePhase) { _, phase in
                // System permission alerts cause inactive; do not cancel their pending response.
                switch phase {
                case .active: busyCalendar.lifecycle(.active)
                case .background: busyCalendar.lifecycle(.background)
                default: busyCalendar.lifecycle(.inactive)
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .EKEventStoreChanged)) { _ in calendarPreferences.refreshAuthorization(); busyCalendar.refresh() }
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
