import SwiftUI

struct NoticeDetail: View {
    let viewModel: NoticeDetailViewModel
    let preferences: CalendarPreferences
    @State private var busyCalendar: BusyCalendarSession

    init(viewModel: NoticeDetailViewModel, preferences: CalendarPreferences) {
        self.viewModel = viewModel
        self.preferences = preferences
        _busyCalendar = State(initialValue: BusyCalendarSession(provider: preferences.provider))
    }

    var body: some View {
        if let state = viewModel.state, let notice = viewModel.notice {
            CalendarExportPresentation { openCalendar in
                VenueMapPresentation { openMap in
                    NoticeDetailSections(state: state,
                        onAddSchedule: notice.schedules.map { phase in
                            CalendarDraftMapper.schedule(phase, detail: notice).map { draft in { openCalendar(draft) } }
                        },
                        onAddApplication: CalendarDraftMapper.application(notice).map { draft in { openCalendar(draft) } },
                        onOpenMap: openMap, busyCalendar: busyCalendar)
                }
            }
            .modifier(BusyCalendarLifecycleModifier(session: busyCalendar, preferences: preferences))
        } else {
            ContentUnavailableView("공고를 불러오지 못했어요", systemImage: "exclamationmark.triangle")
        }
    }
}
